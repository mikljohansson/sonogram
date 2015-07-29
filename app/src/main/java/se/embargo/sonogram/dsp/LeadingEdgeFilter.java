package se.embargo.sonogram.dsp;

public class LeadingEdgeFilter implements ISignalFilter {
	private final int _offset, _step;
	
	public LeadingEdgeFilter(int offset, int step) {
		_offset = offset;
		_step = step;
	}

	public LeadingEdgeFilter() {
		this(0, 1);
	}

	@Override
	public void accept(Item item) {
		final float[] output = item.output;
		for (int i = output.length - 2 + _offset; i >= 2; i -= _step) {
			output[i] = output[i] * 
				(float)(Math.pow(Math.max(output[i] - output[i - 2], 0.0f) + 1.0f, 10) - 1.0f)/*
				(float)(Math.pow(Math.max(output[i] - output[i + 2], 0.0f) + 1.0f, 10) - 1.0f)*/;
		}
	}
}
