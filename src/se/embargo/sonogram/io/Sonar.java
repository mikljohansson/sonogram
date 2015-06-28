package se.embargo.sonogram.io;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import se.embargo.core.concurrent.Parallel;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.sonogram.dsp.CompositeFilter;
import se.embargo.sonogram.dsp.FramerateCounter;
import se.embargo.sonogram.dsp.ISignalFilter;
import se.embargo.sonogram.dsp.Signals;
import android.app.Activity;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

public class Sonar implements ISonar {
	private static final String TAG = "Sonar";
	
	private static final int SAMPLERATE = 48000;
	private static final int PULSEINTERVAL = 80;
	private static final int PULSEDURATION = 3;
	private static final float PULSEAMPLITUDE = 0.9f;
	
	public static final int OPERATOR_LENGTH = SAMPLERATE * PULSEDURATION / 1000;
	public static final int SAMPLES_LENGTH = SAMPLERATE * PULSEINTERVAL / 1000;
	
	/**
	 * Sonar pulse time series.
	 */
	private static final float[] OPERATOR = Signals.createLinearChirp(
		SAMPLERATE, PULSEDURATION, 
		0, (float)SAMPLERATE / 2 - (float)SAMPLERATE / 16);
	
	private ISonarController _controller;
	private final SonarWorker _inputworker = new AudioInputWorker(), _outputworker = new AudioOutputWorker();
	private final Rect _resolution = new Rect(0, 0, SAMPLES_LENGTH * 2, SAMPLES_LENGTH);
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(4, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	/**
	 * Sonar pulse time series.
	 */
	private final float[] _operator = OPERATOR;
	
	/**
	 * DSP filter to apply to samples
	 */
	private ISignalFilter _filter = new CompositeFilter(new AudioSync(), new FramerateCounter());
	
	/**
	 * One time delay to apply to output audio
	 */
	private AtomicInteger _outputDelay = new AtomicInteger(0);
	
	/**
	 * Activity that drives this sonar.
	 */
	private final Activity _context;
	
	/**
	 * Detected distance between microphones in meters.
	 */
	private final IObservableValue<Float> _baseline;
	
	public Sonar(Activity context, IObservableValue<Float> baseline) {
		_context = context;
		_baseline = baseline;
	}
	
	@Override
	public synchronized void init(ISonarController controller, ISignalFilter filter) {
		_controller = controller;
		_controller.setSonarResolution(_resolution);
		_filter = new CompositeFilter(filter, new AudioSync());
	}

	@Override
	public synchronized ISonarController getController() {
		return _controller;
	}

	@Override
	public synchronized ISignalFilter getFilter() {
		return _filter;
	}
	
	@Override
	public synchronized void start() {
		_inputworker.start();
		_outputworker.start();
	}
	
	@Override
	public synchronized void stop() {
		_inputworker.stop();
		_outputworker.stop();
	}
	
	private class FilterTask implements Runnable {
		public final ISignalFilter.Item item;
		private ISonarController _controller;
		private ISignalFilter _filter;
		
		public FilterTask(int samplecount) {
			item = new ISignalFilter.Item(SAMPLERATE, samplecount);
		}
		
		public void init(short[] samples) {
			synchronized (Sonar.this) {
				this._controller = Sonar.this._controller;
				this._filter = Sonar.this._filter;
			}
			
			item.init(_operator, samples, _controller.getSonarWindow(), _controller.getSonarCanvas(), _resolution);
		}

		@Override
		public void run() {
			// Apply the filters
			_filter.accept(item);

			// Reuse this task
			_filterpool.offer(this);
		}
	}
	
	private class AudioInputWorker extends SonarWorker {
		@Override
		public void run() {
			int resolution = SAMPLES_LENGTH;
			int samplecount = (resolution + _operator.length) * 2;
			int chunksize = resolution * 2;
			int channel = AudioFormat.CHANNEL_IN_STEREO;
			short[] samples;
			
			AudioRecord record = new AudioRecord(
				MediaRecorder.AudioSource.CAMCORDER, SAMPLERATE, channel, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, channel, AudioFormat.ENCODING_PCM_16BIT), samplecount));
			
			try {
				int position = samplecount - chunksize;
				record.startRecording();
				samples = new short[samplecount];

				while (!_stop) {
					// Read complete data chunk into last chunksize of buffer
					int count = record.read(samples, position, samplecount - position);
					if (count < 0 || count == AudioRecord.ERROR_INVALID_OPERATION || count == AudioRecord.ERROR_BAD_VALUE) {
						Log.e(TAG, "Audio reader problem: " + count);
						return;
					}
					
					position += count;
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

	private class AudioOutputWorker extends SonarWorker {
		@Override
		public void run() {
			int resolution = SAMPLES_LENGTH;
			int position = 0;
			short[] pulse = Signals.toShort(_operator, PULSEAMPLITUDE);
			short[] silence = new short[_operator.length];
			
			AudioTrack track = new AudioTrack(
				AudioManager.STREAM_MUSIC, SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), resolution), 
				AudioTrack.MODE_STREAM);

			try {
				track.flush();
				track.play();
				
				int duration = resolution;
				while (!_stop) {
					if (position < _operator.length) {
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
	
	public class AudioSync implements ISignalFilter {
		private static final int ADJUSTINTERVAL = PULSEINTERVAL * 5, POSITIVEHITS = 2, TOLERANCE = 15, THRESHOLD = 3;
		
		private boolean _disabled = false;
		private long _ts = 0;
		private int _maxposa = -1, _maxposb = -1, _hitcount = 0;
		
		@Override
		public synchronized void accept(Item item) {
			if (_disabled) {
				return;
			}

			// Analyze matched filter output to find pulse offset
			long ts = System.currentTimeMillis();
			if (ts - _ts >= ADJUSTINTERVAL) {
				final float[] samples = item.output;
				float maxvala = 0.0f, maxvalb = 0.0f;
				int maxposa = 0, maxposb = 0;
				
				// Find maximum values in left/right channels
				for (int i = 0; i < samples.length; i += 2) {
					if (maxvala < Math.abs(samples[i])) {
						maxvala = Math.abs(samples[i]);
						maxposa = i / 2;
					}
					
					if (maxvalb < Math.abs(samples[i + 1])) {
						maxvalb = Math.abs(samples[i + 1]);
						maxposb = (i + 1) / 2;
					}
				}
				
				synchronized (this) {
					Log.i(TAG, "Pulse found at offset " + maxposa);
					int resolution = SAMPLES_LENGTH;

					// Check if pulse was found in same position again (otherwise it's noise)
					if (Math.abs(_maxposa - maxposa) < TOLERANCE && Math.abs(_maxposb - maxposb) < TOLERANCE) {
						if (++_hitcount >= POSITIVEHITS) {
							if (_maxposa < THRESHOLD) {
								_disabled = true;
								final float distance = Math.min(Math.abs(_maxposb - _maxposa), Math.abs(_maxposa - (_maxposb - samples.length / 2)));
								final float baseline = distance / SAMPLERATE * Signals.SPEED;
								Log.i(TAG, "Detected microphone distance is " + baseline + "m");
								Log.i(TAG, "Pulse found at offset " + _maxposa + ", disabling further adjustment");
								
								_context.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										_baseline.setValue(baseline);
									}
								});
							}
							else {
								_outputDelay.set((resolution - maxposa) % resolution);
								Log.i(TAG, "Adjusting for pulse at offset " + maxposa);
							}

							_maxposb = _maxposb - _maxposa;
							if (_maxposb < 0) {
								_maxposb += samples.length / 2;
							}
							
							_maxposa = 0;
							_hitcount = 0;
							_ts = System.currentTimeMillis();
						}
					}
					else {
						_maxposa = maxposa;
						_maxposb = maxposb;
						_hitcount = 0;
					}
				}
			}
		}
	}
}
