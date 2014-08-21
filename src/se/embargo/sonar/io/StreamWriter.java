package se.embargo.sonar.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import se.embargo.sonar.dsp.ISignalFilter;

public class StreamWriter implements ISignalFilter {
	private static final int VERSION = 1; 
	
	private final DataOutputStream _os;
	private boolean _headerWritten = false;
	
	public StreamWriter(OutputStream os) {
		_os = new DataOutputStream(os);
	}
	
	@Override
	public synchronized void accept(Item item) {
		try {
			if (!_headerWritten) {
				_headerWritten = true;
				_os.writeInt(VERSION);
				_os.writeFloat(item.samplerate);
				_os.writeInt(item.samples.length);
				_os.writeInt(item.resolution.width());
				_os.writeInt(item.resolution.height());
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
