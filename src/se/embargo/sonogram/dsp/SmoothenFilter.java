package se.embargo.sonogram.dsp;

public class SmoothenFilter implements ISignalFilter {
	private final int _offset, _step;
	
	public SmoothenFilter(int offset, int step) {
		_offset = offset;
		_step = step;
	}

	public SmoothenFilter() {
		this(0, 1);
	}
	
	@Override
	public void accept(Item item) {
		final float[] output = item.output;
		for (int i = _offset; i < output.length - 6; i += _step) {
			output[i] = (output[i] + output[i + 2] + output[i + 4] + output[i + 6]) / 4.0f;
		}
	}
}
