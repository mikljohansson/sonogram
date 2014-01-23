package se.embargo.sonar;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import se.embargo.core.concurrent.Parallel;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

public class Sonar {
	public static final int SAMPLERATE = 44100;
	public static final int PULSEINTERVAL = 80;
	private static final int PULSEDURATION = 20;
	private static final int PULSERANGE = 10;
	
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
		private final int _chunksize;
		private final short[] _samples;
		private final float[] _output;
		private int _sampleoffset;
		
		public FilterTask(int chunksize) {
			_chunksize = chunksize;
			_samples = new short[_chunksize * 2];
			_output  = new float[_chunksize];
		}
		
		public void init(int sampleoffset, short[] samples) {
			_sampleoffset = sampleoffset;
			System.arraycopy(samples, 0, _samples, 0, samples.length);
		}
		
		@Override
		public void run() {
			// Apply the matched filter convolution
			Signals.convolve(_samples, _pulse, _output);
			//Signals.detect(_output);

			// Update the histogram view
			for (ISonarListener listener : _listeners) {
				listener.receive(_sampleoffset, _output);
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
			AudioRecord record = new AudioRecord(
				MediaRecorder.AudioSource.CAMCORDER, SAMPLERATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), _pulse.length * 2));
			
			int chunksize = _pulse.length;
			int chunkoffset = 0;
			
			short[] samples = new short[chunksize * 2];

			int maxsamples = SAMPLERATE * PULSEINTERVAL / 1000;
			int sampleoffset = 0;
			
			try {
				record.startRecording();

				while (!_stop) {
					// Read complete data chunk into first half of buffer
					chunkoffset += record.read(samples, chunkoffset, chunksize - chunkoffset);
					if (chunkoffset < chunksize) {
						continue;
					}

					// Allocate a new filter task
					FilterTask task = _filterpool.poll();
					if (task == null) {
						task = new FilterTask(chunksize);
					}
				
					// Perform the filter processing on the thread pool
					task.init(sampleoffset, samples);
					_threadpool.submit(task);
					
					// Make room for next data chunk
					System.arraycopy(samples, 0, samples, chunksize, chunksize);
					sampleoffset += chunksize;
					chunkoffset = 0;
					
					// Prepare for next pulse interval
					if (sampleoffset >= maxsamples) {
						sampleoffset = 0;
					}
				}
			}
			finally {
				record.stop();
				record.release();
			}
		}
	}

	private class AudioOutputWorker extends Worker {
		@Override
		public void run() {
			AudioTrack track = new AudioTrack(
				AudioManager.STREAM_MUSIC, SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				Math.max(AudioTrack.getMinBufferSize(SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), _pulse.length * 2), 
				AudioTrack.MODE_STREAM);

			short[] pulse = Signals.toShort(_pulse);
			short[] silence = new short[_pulse.length];
			
			int maxsamples = SAMPLERATE * PULSEINTERVAL / 1000;
			int sampleoffset = 0;
			
			try {
				track.flush();
				track.play();

				while (!_stop) {
					if (sampleoffset < _pulse.length) {
						// Send the sonar pulse
						sampleoffset += track.write(pulse, sampleoffset, pulse.length - sampleoffset);
					}
					else {
						// Silence for rest of pulse interval
						sampleoffset += track.write(silence, 0, Math.min(maxsamples - sampleoffset, silence.length));
					}
					
					// Prepare for next pulse interval
					if (sampleoffset >= maxsamples) {
						sampleoffset = 0;
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
