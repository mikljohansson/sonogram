package se.embargo.sonar;

import se.embargo.sonar.dsp.ISignalFilter;
import android.graphics.Rect;

public interface ISonarController {
	void setSonarResolution(Rect resolution);

	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	Rect getSonarWindow();

	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	Rect getSonarCanvas();
	
	void receive(ISignalFilter.Item item);
}
