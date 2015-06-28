package se.embargo.sonogram.dsp;
import android.util.Log;
import se.embargo.sonogram.dsp.ISignalFilter;

public class FramerateCounter implements ISignalFilter {
	private static final String TAG = "FramerateFilter";
	private long _framestat = 0;
	private long _laststat = 0;
	
	@Override
	public synchronized void accept(Item item) {
		if (_laststat == 0) {
			_framestat = System.nanoTime();
		}
		
		// Calculate the framerate
		if (++_framestat >= 10) {
			long ts = System.nanoTime();
			Log.d(TAG, "Framerate: " + ((double)_framestat / (((double)ts - (double)_laststat) / 1000000000d)));
			
			_framestat = 0;
			_laststat = ts;
		}
	}
}
