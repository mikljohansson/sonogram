package se.embargo.sonogram.dsp;

public class AverageFilter implements ISignalFilter {
	private int _maxgenerations = 4, _generation = 0;
	private int _resolution = -1;
	private float[] _history;

	@Override
	public synchronized void accept(Item item) {
		if (_resolution != item.output.length) {
			_resolution = item.output.length;
			_history = new float[_resolution * _maxgenerations];
		}

		final float[] output = item.output;
		final float[] history = _history;
		int generation = _generation, maxgenerations = _maxgenerations;
		
		for (int i = 0, ig = 0, last = _resolution; i < last; i++, ig += maxgenerations) {
			history[ig + generation] = output[i];
			output[i] = (history[ig] + history[ig + 1] + history[ig + 2] + history[ig + 3]) / maxgenerations;
		}
		
		_generation = (_generation + 1) % _maxgenerations;
	}
}
