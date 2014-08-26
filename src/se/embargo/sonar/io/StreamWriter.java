package se.embargo.sonar.io;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

import se.embargo.sonar.dsp.ISignalFilter;
import android.util.Log;

public class StreamWriter implements ISignalFilter {
	private static final String TAG = "StreamWriter";
	public static final int MAGIC = 0xa24c7709;
	public static final int VERSION = 2; 
	
	public interface IStreamListener {
		abstract void onClosed();
	}
	
	private DataOutputStream _os;
	private boolean _headerWritten = false;
	private final int _itemlimit;
	private int _itemcount = 0;
	private IStreamListener _listener = null;
	
	public StreamWriter(OutputStream os, int itemlimit) {
		_os = new DataOutputStream(new DeflaterOutputStream(new BufferedOutputStream(os)));
		_itemlimit = itemlimit;
	}
	
	public StreamWriter(OutputStream os) {
		this(os, Integer.MAX_VALUE);
	}
	
	public void setListener(IStreamListener listener) {
		_listener = listener;
	}
	
	public synchronized void close() {
		if (_os != null) {
			try {
				_os.close();
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			
			_os = null;
			
			if (_listener != null) {
				_listener.onClosed();
			}
		}
	}
	
	@Override
	public synchronized void accept(Item item) {
		if (_os != null) {
			try {
				if (!_headerWritten) {
					_headerWritten = true;
					_os.writeInt(MAGIC);
					_os.writeInt(VERSION);
					_os.writeFloat(item.samplerate);
					_os.writeInt(item.samples.length);
					_os.writeInt(item.resolution.width());
					_os.writeInt(item.resolution.height());
					
					// Write the operator used for this item
					_os.writeInt(item.operator.length);
					for (int i = 0; i < item.operator.length; i++) {
						_os.writeFloat(item.operator[i]);
					}
				}
				
				for (int i = 0; i < item.samples.length; i++) {
					_os.writeShort(item.samples[i]);
				}
				
				if (++_itemcount >= _itemlimit) {
					close();
				}
			}
			catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				close();
			}
		}
	}
}
