package se.embargo.sonogram.io;

import android.graphics.Rect;

public interface ISonarController {
	/**
	 * Sets the maximum resolution supported by this sonar
	 * @param	resolution	Max supported resolution
	 */
	void setSonarResolution(Rect resolution);

	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	Rect getSonarWindow();

	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	Rect getSonarCanvas();
}
