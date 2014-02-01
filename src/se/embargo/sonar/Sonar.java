package se.embargo.sonar;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import se.embargo.core.concurrent.Parallel;
import se.embargo.sonar.dsp.AverageFilter;
import se.embargo.sonar.dsp.CompositeFilter;
import se.embargo.sonar.dsp.FramerateCounter;
import se.embargo.sonar.dsp.ISignalFilter;
import se.embargo.sonar.dsp.MatchedFilter;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

public class Sonar {
	private static final String TAG = "Sonar";
	
	private static final int SAMPLERATE = 44100;
	private static final int PULSEINTERVAL = 60;
	private static final int PULSEDURATION = 20;
	
	public static final int OPERATOR_LENGTH = SAMPLERATE * PULSEDURATION / 1000;
	public static final int SAMPLES_LENGTH = SAMPLERATE * PULSEINTERVAL / 1000;

	/**
	 * Sonar pulse time series.
	 */
	public static final float[] OPERATOR = Signals.createLinearChirp(SAMPLERATE, PULSEDURATION, (float)SAMPLERATE / 4, (float)SAMPLERATE / 8);
	
	private ISonarController _controller;
	private Worker _inputworker, _outputworker;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(4, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	/**
	 * Sonar pulse time series.
	 */
	private final float[] _pulse = OPERATOR;
	
	/**
	 * DSP filter to apply to samples
	 */
	private ISignalFilter _filter;
	
	/**
	 * True if stereo recording should be used
	 */
	private final boolean _stereo;
	
	/**
	 * One time delay to apply to output audio
	 */
	private AtomicInteger _outputDelay = new AtomicInteger(0);
	
	public Sonar(Context context, boolean stereo) {
		_stereo = stereo;
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
		if (_stereo) {
			_filter = new CompositeFilter(new AudioSync());
			//_filter = new CompositeFilter(new AudioSync(), new SonogramFilter(_pulse), /*new AverageFilter(), */new FramerateCounter());
		}
		else {
			_filter = new CompositeFilter(new AudioSync(), new MatchedFilter(_pulse), new AverageFilter(), new FramerateCounter());
		}

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
				
				int duration = resolution;
				while (!_stop) {
					if (position < _pulse.length) {
						// Send the sonar pulse
						position += track.write(pulse, position, pulse.length - position);
					}
					else {
						// Silence for rest of pulse interval
						position += track.write(silence, 0, Math.min(duration - position, silence.length));
					}
					
					// Prepare for next pulse interval
					if (position >= duration) {
						position = 0;
						duration = resolution + _outputDelay.getAndSet(0);
					}
				}
			}
			finally {
				track.stop();
				track.release();
			}
		}
	}
	
	public class AudioSync implements ISignalFilter, Runnable {
		private static final int ADJUSTINTERVAL = PULSEINTERVAL * 3, POSITIVEHITS = 3, TOLERANCE = 15, THRESHOLD = 3;
		
		private boolean _scheduled = false, _disabled = false;
		private long _ts = 0;
		private short[] _samples;
		private int _maxpos = -1, _hitcount = 0;
		private ExecutorService _executor = Executors.newCachedThreadPool();
		
		@Override
		public synchronized void accept(Item item) {
			if (_disabled) {
				return;
			}
			
			long ts = System.currentTimeMillis();
			if (_ts == 0) {
				_ts = ts;
			}

			// Analyze samples to find pulse offset
			if (ts - _ts >= ADJUSTINTERVAL && !_scheduled) {
				if (_samples == null || _samples.length != item.samples.length) {
					_samples = Arrays.copyOf(item.samples, item.samples.length);
				}
				else {
					System.arraycopy(item.samples, 0, _samples, 0, item.samples.length);
				}
				
				_ts = ts;
				_scheduled = true;
				_executor.submit(this);
			}
		}

		@Override
		public void run() {
			try {
				final short[] samples = _samples;
				final float[] operator = _pulse;
				float maxval = 0, maxshort = Short.MAX_VALUE;
				int maxpos = 0;
				int step = _stereo ? 2 : 1;				
				
				// Apply convolution to find maximum value
				for (int i = 0, il = samples.length - operator.length * step; i < il; i += step) {
					float acc = 0;
					for (int j = 0, is = i; j < operator.length; j++, is += step) {
						acc += ((float)samples[is] / maxshort) * operator[j];
					}
					
					if (maxval < Math.abs(acc)) {
						maxval = Math.abs(acc);
						maxpos = _stereo ? i / 2 : i;
					}
				}
				
				synchronized (this) {
					Log.i(TAG, "Pulse found at offset " + maxpos);
					int resolution = SAMPLERATE * PULSEINTERVAL / 1000;

					// Check if pulse was found in same position again (otherwise it's noise)
					if (Math.abs(_maxpos - maxpos) < TOLERANCE) {
						if (++_hitcount >= POSITIVEHITS) {
							if (_maxpos < THRESHOLD) {
								_disabled = true;
								Log.i(TAG, "Pulse found at offset " + _maxpos + ", disabling further adjustment");
							}
							else {
								_outputDelay.set((resolution - maxpos) % resolution);
								Log.i(TAG, "Adjusting for pulse at offset " + maxpos);
							}
						}
					}
					else {
						_maxpos = maxpos;
						_hitcount = 0;
					}
				}
			}
			finally {
				synchronized (this) {
					_scheduled = false;
				}
			}
		}
	}
}
