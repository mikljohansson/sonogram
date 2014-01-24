package se.embargo.sonar;

import java.util.Arrays;
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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

public class Sonar {
	private static final int SAMPLERATE = 44100;
	private static final int PULSEINTERVAL = 80;
	private static final int PULSEDURATION = 20;
	
	private ISonarListener[] _listeners = new ISonarListener[0];
	private Worker _inputworker, _outputworker;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(16, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	/**
	 * Sonar pulse time series.
	 */
	private float[] _pulse = Signals.createLinearChirp(SAMPLERATE, PULSEDURATION, 0.5f, (float)SAMPLERATE / 8, (float)SAMPLERATE / 4);
	
	/**
	 * DSP filter to apply to samples
	 */
	private ISignalFilter _filter = new CompositeFilter(new MatchedFilter(), new AverageFilter(), new FramerateCounter());
	
	public int getResolution() {
		return SAMPLERATE * PULSEINTERVAL / 1000;
	}
	
	public void addListener(ISonarListener listener) {
		_listeners = Arrays.copyOf(_listeners, _listeners.length + 1);
		_listeners[_listeners.length - 1] = listener;
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
		private ISignalFilter.Item _item;
		
		public FilterTask(int samples) {
			_item = new ISignalFilter.Item(SAMPLERATE, _pulse, samples + _pulse.length);
		}
		
		public void init(short[] samples) {
			_item.init(samples);
		}
		
		@Override
		public void run() {
			// Apply the filters
			_filter.accept(_item);

			// Call the listeners
			for (ISonarListener listener : _listeners) {
				listener.receive(_item.output);
			}

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
			int count = SAMPLERATE * PULSEINTERVAL / 1000;
			int position = 0;
			short[] samples = new short[count + _pulse.length];
			
			AudioRecord record = new AudioRecord(
				MediaRecorder.AudioSource.CAMCORDER, SAMPLERATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), count));
			
			try {
				record.startRecording();

				while (!_stop) {
					// Read complete data chunk into first half of buffer
					position += record.read(samples, position, count - position);
					if (position < count) {
						Thread.sleep(PULSEINTERVAL / 10);
						continue;
					}

					// Allocate a new filter task
					FilterTask task = _filterpool.poll();
					if (task == null) {
						task = new FilterTask(count);
					}
				
					// Perform the filter processing on the thread pool
					task.init(samples);
					_threadpool.submit(task);
					
					// Make room for next data chunk
					System.arraycopy(samples, 0, samples, _pulse.length, count);
					position = 0;
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
			int count = SAMPLERATE * PULSEINTERVAL / 1000;
			int position = 0;
			short[] pulse = Signals.toShort(_pulse);
			short[] silence = new short[_pulse.length];
			
			AudioTrack track = new AudioTrack(
				AudioManager.STREAM_MUSIC, SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), count), 
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
						position += track.write(silence, 0, Math.min(count - position, silence.length));
					}
					
					// Prepare for next pulse interval
					if (position >= count) {
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
