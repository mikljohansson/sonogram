package se.embargo.sonar;

public class Signals {
	/**
	 * @param operator	Reversed time series of pulse to match
	 */
	public static void convolve(short[] data, double[] operator, double[] output) {
		for (int i = 0; i < data.length - operator.length; i++) {
			output[i] = 0;
			
			for (int j = operator.length - 1; j >= 0; j--) {
				output[i] += ((double)data[i + j] / Short.MAX_VALUE) * operator[j];
			}
		}
	}

	/**
	 * Detects spikes from pulses in the output from a convolution.
	 * @param data	Output from convolve()
	 * @param snr	Signal to noise ratio [0, 1]
	 */
	public static void detect(double[] data, double snr) {
		double sum = 0;
		for (int i = 0; i < data.length; i++) {
			sum += Math.max(0, data[i]);
		}
		
		double mean = sum / data.length;
		double threshold = mean * snr;
		
		for (int i = 0; i < data.length; i++) {
			if (data[i] < threshold) {
				data[i] = 0.0d;
			}
		}
	}
	
	/**
	 * Detects spikes from pulses in the output from a convolution.
	 * @param data	Output from convolve()
	 */
	public static void detect(double[] data) {
		detect(data, 2.5d);
	}
	
	/**
	 * Create a tapered chirp with a linear frequency sweep
	 * @link  http://www.mbari.org/data/mbsystem/sonarfunction/SubbottomProcessing/subbottomdataprocessing.html
	 * @param samplerate	Sample rate of generated pulse in Hz
	 * @param duration		Pulse length in milliseconds
	 * @param amplitude		Maximum/minimum amplitude
	 * @param frequency		Pulse base/minimum frequency in Hz
	 * @param bandwidth		Pulse bandwidth in Hz
	 * @return				A frequency modulated chirp
	 */
	public static double[] createLinearChirp(int samplerate, int duration, double amplitude, int frequency, int bandwidth) {
		double[] pulse = new double[samplerate * duration / 1000];

		// sin(2 * pi * (f2 - f1) / (2 * d) * ((x/s)^2) + 2 * pi * f1 * (x/s))  *  sin(2 * pi * (1 / d / 2) * (x/s)) ^ 0.25
		double 
			t = (double)duration / 1000d,
			step = 2d * Math.PI * bandwidth / (2d * t) / ((double)samplerate * samplerate),
			step2 = 2d * Math.PI * frequency / samplerate,
			taper = 2d * Math.PI * (1d / t / 2d) / samplerate;
		
		for (int i = 0; i < pulse.length; i++) {
			pulse[i] = Math.sin(step * i * i + step2 * i) * Math.pow(Math.sin(taper * i), 0.25) * amplitude;
		}
		
		return pulse;
	}
	
	/**
	 * Rebases an time series to the short data type
	 * @param data	Time series in range [-1, 1] to rebase into [Short.MIN_VALUE, Short.MAX_VALUE] 
	 * @return		Rebased of time series
	 */
	public static short[] toShort(double[] data) {
		short[] result = new short[data.length];
		for (int i = 0; i < data.length; i++) {
			result[i] = (short)(data[i] * Short.MAX_VALUE);
		}
		
		return result;
	}

	/**
	 * Reverses a time series
	 * @param data	Time series to reverse
	 * @return		Reverse of input series
	 */
	public static double[] reverse(double[] data) {
		double[] result = new double[data.length];
		for (int i = 0, j = data.length - 1; i < data.length; i++, j--) {
			result[j] = data[i];
		}
		
		return result;
	}
}
