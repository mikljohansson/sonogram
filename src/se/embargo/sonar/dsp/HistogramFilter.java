package se.embargo.sonar.dsp;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;

public class HistogramFilter implements ISignalFilter {
	private final int _offset;
	private final FilterBody _body = new FilterBody();
	private boolean _reduce = false;
	
	public HistogramFilter(int offset) {
		_offset = offset;
	}

	public HistogramFilter() {
		this(0);
	}
	
	public HistogramFilter reduce(boolean reduce) {
		_reduce = reduce;
		return this;
	}

	@Override
	public void accept(Item item) {
		if (item.output.length != item.canvas.width()) {
			item.output = new float[item.canvas.width()];
		}
		
		item.maxvalue = 0;
		
		// Apply filter in parallel over output columns
		Parallel.forRange(_body, item, 0, item.output.length);
	}
	
	private class FilterBody implements IForBody<Item> {
		@Override
		public void run(Item item, int it, int last) {
			final float[] operator = item.operator;
			final short[] samples = item.samples;
			final float[] output = item.output;
			final float maxshort = Short.MAX_VALUE;
			
			final float rightdistance = Signals.distance(item.samplerate, (float)(item.window.right - item.resolution.left) / 2.0f),
						leftdistance = Signals.distance(item.samplerate, (float)(item.window.left - item.resolution.left) / 2.0f);
			final float outputstep = (rightdistance - leftdistance) / (float)item.output.length;
			float maxvalue = 0;
			
			for (; it < last; it++) {
				float si = Signals.sample(item.samplerate, leftdistance + it * outputstep),
					  sl = Signals.sample(item.samplerate, leftdistance + (it + 1) * outputstep);
				float prev = output[it];
				output[it] = 0.0f;

				final int samplesteps = (int)Math.max(Math.floor(sl - si), 1.0f);
				for (int i = 0; i < samplesteps; i++) {
					//float r2 = si - (float)Math.floor(si), r1 = 1.0f - r2;
					float acc = 0;
					
					for (int j = 0, jl = operator.length, vi = (int)si * 2 + _offset; j < jl; j++, vi += 2) {
						//float sample = (float)samples[vi] * r1 + (float)samples[vi + _step] * r2;
						float sample = (float)samples[vi];
						acc += (sample / maxshort) * operator[j];
					}
					
					output[it] = Math.max(output[it], Math.abs(acc));
					//output[it] += Math.abs(acc);
				}
				
				if (_reduce) {
					output[it] *= prev; 
				}
				
				maxvalue = Math.max(maxvalue, output[it]);
			}
			
			synchronized (item) {
				item.maxvalue = Math.max(item.maxvalue, maxvalue);
			}
		}
	}
}
