package se.embargo.sonar.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.InflaterInputStream;

import se.embargo.core.concurrent.Parallel;
import se.embargo.sonar.dsp.ISignalFilter;
import android.graphics.Rect;
import android.util.Log;

public class StreamReader implements ISonar {
	private static final String TAG = "StreamReader";
	
	private final FileInputStream _fis;
	private ISonarController _controller;
	private ISignalFilter _filter;
	private SonarWorker _inputworker;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(4, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	public StreamReader(FileInputStream is) {
		_fis = is;
	}

	@Override
	public void setController(ISonarController controller) {
		_controller = controller;
	}

	@Override
	public ISignalFilter getFilter() {
		return _filter;
	}

	@Override
	public void setFilter(ISignalFilter filter) {
		_filter = filter;
	}

	@Override
	public void start() {
		_inputworker = new AudioInputWorker();
		_inputworker.start();
	}

	@Override
	public void stop() {
		_inputworker.stop();
	}
	
	private class FilterTask implements Runnable {
		public final ISignalFilter.Item item;
		
		public FilterTask(float samplerate, int samplecount) {
			item = new ISignalFilter.Item(samplerate, samplecount);
		}
		
		public void init(short[] samples, Rect resolution) {
			item.init(samples, _controller.getSonarWindow(), _controller.getSonarCanvas(), resolution);
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
		private DataInputStream _dis;
		private int _samplecount, _width, _heigth;
		private float _samplerate;
		private long _startPosition;
		
		@Override
		public void run() {
			try {
				_startPosition = _fis.getChannel().position();
				startReading();
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				return;
			}

			Rect resolution = new Rect(0, 0, _width, _heigth);
			_controller.setSonarResolution(resolution);
			
			short[] samples = new short[_samplecount];
			try {
				while (!_stop) {
					long ts1 = System.currentTimeMillis();
					
					// Read chunk into local buffer
					for (int i = 0; i < samples.length; i++) {
						try {
							samples[i] = _dis.readShort();
						}
						catch (EOFException e) {
							_fis.getChannel().position(_startPosition);
							startReading();
							continue;
						}
					}
	
					// Allocate a new filter task
					FilterTask task = _filterpool.poll();
					if (task == null) {
						task = new FilterTask(_samplerate, _samplecount);
					}
				
					// Perform the filter processing on the thread pool
					task.init(samples, resolution);
					_threadpool.submit(task);
					
					// Sleep for a while
					long ts2 = System.currentTimeMillis();
					long remaining = (long)(1000.0 / _samplerate) * _samplecount - (ts2 - ts1);
					
					if (remaining > 0) {
						Thread.sleep(remaining);
					}
				}
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			catch (InterruptedException e) {}
		}
		
		private void startReading() throws IOException {
			_dis = new DataInputStream(new InflaterInputStream(new BufferedInputStream(_fis)));
			/*int version = */_dis.readInt();
			_samplerate = _dis.readFloat();
			_samplecount = _dis.readInt();
			_width = _dis.readInt();
			_heigth = _dis.readInt();
		}
	}
}
