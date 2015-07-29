package se.embargo.sonogram.dsp;

public class AmplificationFilter implements ISignalFilter {
	private final int _offset, _step;
	
	public AmplificationFilter(int offset, int step) {
		_offset = offset;
		_step = step;
	}

	public AmplificationFilter() {
		this(0, 1);
	}

	@Override
	public void accept(Item item) {
		final float[] output = item.output;
		for (int i = _offset; i < output.length; i += _step) {
			output[i] = (float)Math.log(Math.log(output[i] + 1.0f) + 1.0f);
		}
	}
}
