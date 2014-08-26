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
	
	private DataOutputStream _os;
	private boolean _headerWritten = false;
	
	public StreamWriter(OutputStream os) {
		_os = new DataOutputStream(new DeflaterOutputStream(new BufferedOutputStream(os)));
	}
	
	public synchronized void close() {
		try {
			_os.close();
		}
		catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		_os = null;
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
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
