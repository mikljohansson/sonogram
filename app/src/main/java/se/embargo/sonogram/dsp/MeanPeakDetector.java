package se.embargo.sonogram.dsp;

/*
 * @link	http://stackoverflow.com/questions/22583391/peak-recognition-in-realtime-timeseries-data/22640362#22640362
 */
public class MeanPeakDetector implements ISignalFilter {
	@Override
	public void accept(Item item) {
		final float[] output = item.output;
		apply(output, 0);
		apply(output, 1);
	}

	private void apply(final float[] output, int offset) {
		final float diff = 1.75f;
		
		float mean = output[output.length - 2], stddev = 0.0f;
		for (int i = offset; i < output.length; i += 2) {
			float sample = output[i];
			
			if (output[i] <= mean + diff * stddev) {
				output[i] = 0.0f;
			}

			stddev = (stddev + Math.abs(sample - mean)) / 2.0f;
			mean = (sample + (sample - mean) / 10.0f) / 2.0f;
		}
	}
}
