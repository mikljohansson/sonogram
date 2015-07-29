package se.embargo.sonogram.dsp;

/**
 * @link	http://www.imatest.com/docs/sharpening/
 */
public class SharpenFilter implements ISignalFilter {
	private final int _offset, _step;
	private final float _sharpen = 0.3f;
	
	public SharpenFilter(int offset, int step) {
		_offset = offset;
		_step = step;
	}

	public SharpenFilter() {
		this(0, 1);
	}

	@Override
	public void accept(Item item) {
		final float[] output = item.output;
		for (int i = _offset; i < output.length - 4; i += _step) {
			// Lsharp(x) = [ L(x) - (ksharp /2) * (L(x-V) + L(x+V)) ] / (1- ksharp )
			output[i] = (output[i] - (_sharpen / 2.0f) * (output[i + 2] + output[i + 4])) / (1.0f - _sharpen);
		}
	}
}
