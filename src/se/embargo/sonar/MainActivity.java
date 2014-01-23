package se.embargo.sonar;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import se.embargo.core.concurrent.Parallel;
import se.embargo.sonar.widget.HistogramView;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockFragmentActivity {
	private static final String TAG = "MainActivity";
	private static final int SAMPLERATE = 44100;
	private static final int PULSEINTERVAL = 80;
	private static final int PULSEDURATION = 20;
	private static final int PULSERANGE = 10;
	
	private Worker _inputworker, _outputworker;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(16), new ThreadPoolExecutor.DiscardOldestPolicy());
	private final Queue<FilterTask> _filterpool = new ConcurrentLinkedQueue<FilterTask>();
	
	private float[] _pulse = Signals.createLinearChirp(SAMPLERATE, PULSEDURATION, 0.5f, (float)SAMPLERATE / 8, (float)SAMPLERATE / 4);
	
	private HistogramView _histogramView;
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.main_activity);
		_histogramView = (HistogramView)findViewById(R.id.histogram);
		_histogramView.setResolution(SAMPLERATE * PULSEINTERVAL / 1000);
		//_histogramView.setZoom((float)PULSEINTERVAL / PULSERANGE);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		_inputworker = new InputWorker();
		_inputworker.start();
		
		_outputworker = new OutputWorker();
		_outputworker.start();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		_inputworker.stop();
		_outputworker.stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			default:
				return super.onOptionsItemSelected(item);
		}
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
			_histogramView.update(_sampleoffset, _output);

			// Reuse this task
			_filterpool.add(this);
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
	
	private class InputWorker extends Worker {
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

	private class OutputWorker extends Worker {
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
