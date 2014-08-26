package se.embargo.sonar.dsp;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import android.annotation.SuppressLint;

public class MatchedFilter implements ISignalFilter {
	private final int _offset;
	private final FilterBody _body = new FilterBody();
	
	public MatchedFilter(int offset) {
		_offset = offset;
	}

	public MatchedFilter() {
		this(0);
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
		@SuppressLint("FloatMath")
		public void run(Item item, int it, int last) {
			final float[] operator = item.operator;
			final short[] samples = item.samples;
			final float[] output = item.output;
			final float maxshort = Short.MAX_VALUE;
			final float sampleratio = (float)item.window.width() / (float)item.output.length;
			final int samplesteps = (int)Math.min(Math.ceil(sampleratio), 1.0f);
			float maxvalue = 0;
			
			for (; it < last; it++) {
				float si = (float)item.window.left + sampleratio * it;
				output[it] = 0.0f;
				
				for (int i = 0; i < samplesteps; i++) {
					//float r2 = si - FloatMath.floor(si), r1 = 1.0f - r2;
					float acc = 0;
					
					for (int j = 0, jl = operator.length, vi = (int)si * 2 + _offset; j < jl; j++, vi += 2) {
						//float sample = (float)samples[vi] * r1 + (float)samples[vi + _step] * r2;
						float sample = (float)samples[vi];
						acc += (sample / maxshort) * operator[j];
					}
					
					output[it] += Math.abs(acc);
				}
				
				maxvalue = Math.max(maxvalue, output[it]);
			}
			
			synchronized (item) {
				item.maxvalue = Math.max(item.maxvalue, maxvalue);
			}
		}
	}
}
