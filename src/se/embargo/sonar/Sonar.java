package se.embargo.sonar;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import se.embargo.core.concurrent.Parallel;
import se.embargo.sonar.dsp.AverageFilter;
import se.embargo.sonar.dsp.CompositeFilter;
import se.embargo.sonar.dsp.FramerateCounter;
import se.embargo.sonar.dsp.ISignalFilter;
import se.embargo.sonar.dsp.MatchedFilter;
import se.embargo.sonar.dsp.SonogramFilter;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

public class Sonar {
	private static final int SAMPLERATE = 44100;
	private static final int PULSEINTERVAL = 80;
	private static final int PULSEDURATION = 20;
	
	private ISonarController _controller;
	private Worker _inputworker, _outputworker;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(4, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	/**
	 * Sonar pulse time series.
	 */
	private final float[] _pulse = Signals.createLinearChirp(SAMPLERATE, PULSEDURATION, (float)SAMPLERATE / 4, (float)SAMPLERATE / 8);
	
	/**
	 * DSP filter to apply to samples
	 */
	private final ISignalFilter _filter;
	
	/**
	 * True if stereo recording should be used
	 */
	private final boolean _stereo;
	
	public Sonar(Context context, boolean stereo) {
		_stereo = stereo;
		
		if (_stereo) {
			_filter = new CompositeFilter(new SonogramFilter(_pulse), /*new AverageFilter(), */new FramerateCounter());
		}
		else {
			_filter = new CompositeFilter(new MatchedFilter(_pulse), new AverageFilter(), new FramerateCounter());
		}
	}
	
	
	public void setController(ISonarController controller) {
		_controller = controller;

		if (_stereo) {
			int resolution = SAMPLERATE * PULSEINTERVAL / 1000;
			int height = (int)Math.floor(Math.sqrt(resolution * resolution / 2)) - 10;
			_controller.setSonarResolution(new Rect(0, 0, height * 2, height));
		}
		else {
			int resolution = SAMPLERATE * PULSEINTERVAL / 1000;
			_controller.setSonarResolution(new Rect(0, 0, resolution, 1));
		}
	}
	
	public void start() {
		_inputworker = new AudioInputWorker();
		_inputworker.start();
		
		_outputworker = new AudioOutputWorker();
		_outputworker.start();
	}
	
	public void stop() {
		_inputworker.stop();
		_outputworker.stop();
	}
	
	private class FilterTask implements Runnable {
		public final ISignalFilter.Item item;
		
		public FilterTask(int samplecount) {
			item = new ISignalFilter.Item(SAMPLERATE, samplecount);
		}
		
		public void init(short[] samples) {
			item.init(samples, _controller.getSonarWindow(), _controller.getSonarCanvas());
		}

		@Override
		public void run() {
			// Apply the filters
			_filter.accept(item);

			// Forward the filter output
			_controller.receive(item);

			// Reuse this task
			_filterpool.offer(this);
		}
	}
	
	public abstract class Worker implements Runnable {
		private final Thread _thread = new Thread(this);
		protected volatile boolean _stop = false;
		
		public void start() {
			_thread.start();
		}
		
		public void stop() {
			_stop = true;
			_thread.interrupt();
		}
	}
	
	private class AudioInputWorker extends Worker {
		@Override
		public void run() {
			int resolution = SAMPLERATE * PULSEINTERVAL / 1000;
			int samplecount, chunksize;
			int channel;
			short[] samples;
			
			if (_stereo) {
				samplecount = (resolution + _pulse.length) * 2;
				chunksize = resolution * 2;
				channel = AudioFormat.CHANNEL_IN_STEREO;
			}
			else {
				samplecount = resolution + _pulse.length;
				chunksize = resolution;
				channel = AudioFormat.CHANNEL_IN_MONO;
			}
			
			AudioRecord record = new AudioRecord(
				MediaRecorder.AudioSource.DEFAULT, SAMPLERATE, channel, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, channel, AudioFormat.ENCODING_PCM_16BIT), samplecount));
			
			try {
				int position = samplecount - chunksize;
				record.startRecording();
				samples = new short[samplecount];

				while (!_stop) {
					// Read complete data chunk into last chunksize of buffer
					position += record.read(samples, position, samplecount - position);
					if (position < samplecount) {
						Thread.sleep(PULSEINTERVAL / 10);
						continue;
					}

					// Allocate a new filter task
					FilterTask task = _filterpool.poll();
					if (task == null) {
						task = new FilterTask(samplecount);
					}
				
					// Perform the filter processing on the thread pool
					task.init(samples);
					_threadpool.submit(task);
					
					// Save last/newest section of previous buffer as the first part
					System.arraycopy(samples, chunksize, samples, 0, samplecount - chunksize);
					position = samplecount - chunksize;
				}
			}
			catch (InterruptedException e) {}
			finally {
				record.stop();
				record.release();
			}
		}
	}

	private class AudioOutputWorker extends Worker {
		@Override
		public void run() {
			int resolution = SAMPLERATE * PULSEINTERVAL / 1000;
			int position = 0;
			short[] pulse = Signals.toShort(_pulse, 0.5f);
			short[] silence = new short[_pulse.length];
			
			AudioTrack track = new AudioTrack(
				AudioManager.STREAM_MUSIC, SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), resolution), 
				AudioTrack.MODE_STREAM);

			try {
				track.flush();
				track.play();

				while (!_stop) {
					if (position < _pulse.length) {
						// Send the sonar pulse
						position += track.write(pulse, position, pulse.length - position);
					}
					else {
						// Silence for rest of pulse interval
						position += track.write(silence, 0, Math.min(resolution - position, silence.length));
					}
					
					// Prepare for next pulse interval
					if (position >= resolution) {
						position = 0;
					}
				}
			}
			finally {
				track.stop();
				track.release();
			}
		}
	}
}
