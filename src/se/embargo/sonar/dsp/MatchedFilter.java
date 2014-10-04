package se.embargo.sonar.dsp;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import android.annotation.SuppressLint;

public class MatchedFilter implements ISignalFilter {
	private final FilterBody _body = new FilterBody();
	
	@Override
	public void accept(Item item) {
		// Convolve both channels
		Parallel.forRange(_body, item, 0, item.samples.length - item.operator.length * 2);
	}
	
	private class FilterBody implements IForBody<Item> {
		@Override
		@SuppressLint("FloatMath")
		public void run(Item item, int i, int last) {
			final float[] operator = item.operator;
			final short[] samples = item.samples;
			final float[] matched = item.matched;
			
			// Divisor to get samples into [-1.0, 1.0] range
			final float divisor = (float)Short.MAX_VALUE;
			
			for (; i < last; i++) {
				float acc = 0;
				for (int j = 0, jl = operator.length, si = i; j < jl; j++, si += 2) {
					acc += ((float)samples[si] / divisor) * operator[j];
				}
				
				matched[i] = Math.abs(acc);
			}
		}
	}
}
