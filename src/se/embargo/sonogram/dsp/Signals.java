package se.embargo.sonogram.dsp;

public class Signals {
	/**
	 * Speed of sound in meters/second
	 */
	public static final float SPEED = 340.29f;

	/**
	 * @param operator	Reversed time series of pulse to match
	 */
	public static void convolve(short[] data, float[] operator, float[] output) {
		for (int i = 0; i < data.length - operator.length; i++) {
			output[i] = 0;
			
			for (int j = operator.length - 1; j >= 0; j--) {
				output[i] += ((float)data[i + j] / Short.MAX_VALUE) * operator[j];
			}
		}
	}

	/**
	 * Detects spikes from pulses in the output from a convolution.
	 * @param data	Output from convolve()
	 * @param snr	Signal to noise ratio [0, 1]
	 */
	public static void detect(float[] data, float snr) {
		float sum = 0;
		for (int i = 0; i < data.length; i++) {
			sum += Math.max(0, data[i]);
		}
		
		float mean = sum / data.length;
		float threshold = mean * snr;
		
		for (int i = 0; i < data.length; i++) {
			if (data[i] < threshold) {
				data[i] = 0.0f;
			}
		}
	}
	
	/**
	 * Detects spikes from pulses in the output from a convolution.
	 * @param data	Output from convolve()
	 */
	public static void detect(float[] data) {
		detect(data, 2.5f);
	}
	
	/**
	 * Distance in meters to an echo
	 * @param samplerate	Sample rate of input signal
	 * @param sampleix		Sample index of echo
	 * @return				Distance to echo in meters
	 */
	public static float distance(float samplerate, float sampleix) {
		return sampleix / 2.0f  / samplerate * SPEED;
	}

	/**
	 * Sample index where an echo could be found
	 * @param samplerate	Sample rate of input signal
	 * @param distance		Distance to expected object
	 * @return				Index of sample where an echo could be found
	 */
	public static float sample(float samplerate, float distance) {
		return distance * 2.0f * samplerate / SPEED; 
	}
	
	/**
	 * Create a tapered chirp with a linear frequency sweep
	 * @link  http://www.mbari.org/data/mbsystem/sonarfunction/SubbottomProcessing/subbottomdataprocessing.html
	 * @param samplerate	Sample rate of generated pulse in Hz
	 * @param duration		Pulse length in milliseconds
	 * @param amplitude		Maximum/minimum amplitude
	 * @param start			Pulse minimum frequency in Hz
	 * @param end			Pulse maximum frequency in Hz
	 * @return				A frequency modulated chirp
	 */
	public static float[] createLinearChirp(int samplerate, int duration, float start, float end) {
		float[] pulse = new float[samplerate * duration / 1000];

		// sin(2 * pi * (f2 - f1) / (2 * d) * ((x/s)^2) + 2 * pi * f1 * (x/s))  *  sin(2 * pi * (1 / d / 2) * (x/s)) ^ 0.25
		float 
			t = (float)duration / 1000f,
			step = 2f * (float)Math.PI * (end - start) / (2f * t) / ((float)samplerate * samplerate),
			step2 = 2f * (float)Math.PI * start / samplerate,
			taper = 2f * (float)Math.PI * (1f / t / 2f) / samplerate;
		
		for (int i = 0; i < pulse.length; i++) {
			pulse[i] = (float)(Math.sin(step * i * i + step2 * i) * Math.pow(Math.sin(taper * i), 0.25f));
		}
		
		return pulse;
	}
	
	/**
	 * Rebases an time series to the short data type
	 * @param data	Time series in range [-1, 1] to rebase into [Short.MIN_VALUE, Short.MAX_VALUE] 
	 * @return		Rebased of time series
	 */
	public static short[] toShort(float[] data, float amplitude, int stride) {
		short[] result = new short[data.length * stride];
		for (int i = 0, j = 0; i < data.length; i++, j += stride) {
			result[j] = (short)(data[i] * amplitude * Short.MAX_VALUE);
		}
		
		return result;
	}

	/**
	 * Rebases an time series to the short data type
	 * @param data	Time series in range [-1, 1] to rebase into [Short.MIN_VALUE, Short.MAX_VALUE] 
	 * @return		Rebased of time series
	 */
	public static short[] toShort(float[] data, float amplitude) {
		return toShort(data, amplitude, 1);
	}
	
	/**
	 * Reverses a time series
	 * @param data	Time series to reverse
	 * @return		Reverse of input series
	 */
	public static float[] reverse(float[] data) {
		float[] result = new float[data.length];
		for (int i = 0, j = data.length - 1; i < data.length; i++, j--) {
			result[j] = data[i];
		}
		
		return result;
	}
}
