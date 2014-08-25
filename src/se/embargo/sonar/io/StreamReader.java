package se.embargo.sonar.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
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
	
	private final DataInputStream _is;
	private ISonarController _controller;
	private ISignalFilter _filter;
	private SonarWorker _inputworker;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(4, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	public StreamReader(InputStream is) {
		_is = new DataInputStream(new InflaterInputStream(new BufferedInputStream(is)));
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
		@Override
		public void run() {
			int samplecount;
			float samplerate;
			int width, heigth;
			
			try {
				/*int version = */_is.readInt();
				samplerate = _is.readFloat();
				samplecount = _is.readInt();
				width = _is.readInt();
				heigth = _is.readInt();
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				return;
			}

			Rect resolution = new Rect(0, 0, width, heigth);
			_controller.setSonarResolution(resolution);
			
			short[] samples = new short[samplecount];
			try {
				while (!_stop) {
					long ts1 = System.currentTimeMillis();
					
					// Read chunk into local buffer
					for (int i = 0; i < samples.length; i++) {
						samples[i] = _is.readShort();
					}
	
					// Allocate a new filter task
					FilterTask task = _filterpool.poll();
					if (task == null) {
						task = new FilterTask(samplerate, samplecount);
					}
				
					// Perform the filter processing on the thread pool
					task.init(samples, resolution);
					_threadpool.submit(task);
					
					// Sleep for a while
					long ts2 = System.currentTimeMillis();
					long remaining = (long)(1000.0 / samplerate) * samplecount - (ts2 - ts1);
					Thread.sleep(remaining);					
				}
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			catch (InterruptedException e) {}
		}
	}
}
