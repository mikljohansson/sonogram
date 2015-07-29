package se.embargo.sonogram.dsp;

public class MonoFilter implements ISignalFilter {
	private final int _offset, _step;
	
	public MonoFilter(int offset, int step) {
		_offset = offset;
		_step = step;
	}

	public MonoFilter() {
		this(0, 2);
	}

	@Override
	public void accept(Item item) {
		final float[] output = item.output;
		
		for (int i = _offset; i < output.length - 1; i += _step) {
			output[i + 1] = output[i];
		}
	}
}
