package se.embargo.sonar.dsp;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import android.annotation.SuppressLint;
import android.util.FloatMath;

public class MatchedFilter implements ISignalFilter {
	private final float[] _operator;
	private final FilterBody _body = new FilterBody();
	
	public MatchedFilter(float[] operator) {
		_operator = operator;
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
			final float[] operator = _operator;
			final short[] samples = item.samples;
			final float[] output = item.output;
			final float maxshort = Short.MAX_VALUE;
			final float samplestep = (float)item.window.width() / (float)item.output.length;
			float maxvalue = 0;
			
			for (float si = (float)item.window.left + samplestep * it; it < last; it++, si += samplestep) {
				float r2 = si - FloatMath.floor(si), r1 = 1.0f - r2;
				float acc = 0;
				
				for (int j = 0, jl = operator.length, vi = (int)si; j < jl; j++, vi++) {
					float sample = (float)samples[vi * 2] * r1 + (float)samples[vi * 2 + 2] * r2;
					acc += (sample / maxshort) * operator[j];
				}
				
				output[it] = acc;
				maxvalue = Math.max(maxvalue, acc);
			}
			
			synchronized (item) {
				item.maxvalue = Math.max(item.maxvalue, maxvalue);
			}
		}
	}
}
