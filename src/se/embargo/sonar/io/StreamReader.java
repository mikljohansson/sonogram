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
	
	private final String _path;
	private ISonarController _controller;
	private ISignalFilter _filter;
	private Rect _resolution;
	private final SonarWorker _inputworker = new AudioInputWorker();
	
	private DataInputStream _dis;
	private int _samplecount;
	private float _samplerate;
	private float[] _operator;
	
	private static ExecutorService _threadpool = new ThreadPoolExecutor(
		Parallel.getNumberOfCores(), Parallel.getNumberOfCores(), 0, TimeUnit.MILLISECONDS, 
		new ArrayBlockingQueue<Runnable>(4, false), new ThreadPoolExecutor.DiscardOldestPolicy());
	
	private final Queue<FilterTask> _filterpool = new ArrayBlockingQueue<FilterTask>(Parallel.getNumberOfCores(), false);
	
	public StreamReader(String path) {
		_path = path;
	}

	@Override
	public synchronized void init(ISonarController controller, ISignalFilter filter) {
		_controller = controller;
		_filter = filter;
		
		if (_resolution == null) {
			try {
				readHeader();
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				return;
			}
		}
		
		_controller.setSonarResolution(_resolution);
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
	}

	@Override
	public synchronized void stop() {
		_inputworker.stop();
	}
	
	private void readHeader() throws IOException {
		if (_dis != null) {
			_dis.close();
		}
		
		_dis = new DataInputStream(new InflaterInputStream(new BufferedInputStream(new FileInputStream(_path))));
		int magic = _dis.readInt();
		if (magic != StreamWriter.MAGIC) {
			throw new IOException("Invalid magic number: " + magic);
		}
		
		int version = _dis.readInt();
		if (version != StreamWriter.VERSION) {
			throw new IOException("Unsupported version number: " + version);
		}
		
		_samplerate = _dis.readFloat();
		/*float baseline = */_dis.readFloat();
		_samplecount = _dis.readInt();
		int width = _dis.readInt();
		int heigth = _dis.readInt();
		_resolution = new Rect(0, 0, width, heigth);
		
		// Read the operator used for this item
		int operatorlength = _dis.readInt();
		_operator = new float[operatorlength];
		for (int i = 0; i < _operator.length; i++) {
			_operator[i] = _dis.readFloat();
		}
	}
	
	private class FilterTask implements Runnable {
		public final ISignalFilter.Item item;
		private ISonarController _controller;
		private ISignalFilter _filter;
		
		public FilterTask(float samplerate, int samplecount) {
			item = new ISignalFilter.Item(samplerate, samplecount);
		}
		
		public void init(float[] operator, short[] samples, Rect resolution) {
			synchronized (StreamReader.this) {
				this._controller = StreamReader.this._controller;
				this._filter = StreamReader.this._filter;
			}
			
			item.init(operator, samples, _controller.getSonarWindow(), _controller.getSonarCanvas(), resolution);
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
			short[] samples = new short[_samplecount];
			_controller.setSonarResolution(_resolution);
			
			try {
				while (!_stop) {
					try {
						long ts1 = System.currentTimeMillis();
						
						// Read chunk into local buffer
						for (int i = 0; i < samples.length; i++) {
							samples[i] = _dis.readShort();
						}
		
						// Allocate a new filter task
						FilterTask task = _filterpool.poll();
						if (task == null) {
							task = new FilterTask(_samplerate, _samplecount);
						}
					
						// Perform the filter processing on the thread pool
						task.init(_operator, samples, _resolution);
						_threadpool.submit(task);
						
						// Sleep for a while
						long ts2 = System.currentTimeMillis();
						long remaining = (long)(1000.0 / _samplerate) * _samplecount - (ts2 - ts1);
						
						if (remaining > 0) {
							Thread.sleep(remaining);
						}
					}
					catch (EOFException e) {
						readHeader();
					}
				}
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			catch (InterruptedException e) {}
		}
	}
}
