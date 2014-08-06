package se.embargo.sonar.dsp;

import java.util.Arrays;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import android.annotation.SuppressLint;

public class MatchedFilter implements ISignalFilter {
	private final float[] _operator;
	private final int _step, _offset;
	private final FilterBody _body = new FilterBody();
	
	public MatchedFilter(float[] operator, int step, int offset) {
		_operator = operator;
		_step = step;
		_offset = offset;
	}

	public MatchedFilter(float[] operator) {
		this(operator, 1, 0);
	}

	@Override
	public void accept(Item item) {
		if (item.output.length != item.canvas.width()) {
			item.output = new float[item.canvas.width()];
		}
		
		Arrays.fill(item.output, 0);
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
			final float sampleratio = (float)item.window.width() / (float)item.output.length;
			final float samplestep = Math.min(sampleratio, 1.0f), 
						outputstep = Math.max((float)item.window.width() / (float)item.output.length, 1.0f);
			float maxvalue = 0;
			
			for (float si = (float)item.window.left + sampleratio * it, oi = it; it < last; si += samplestep, oi += outputstep, it = (int)oi) {
				//float r2 = si - FloatMath.floor(si), r1 = 1.0f - r2;
				float acc = 0;
				
				for (int j = 0, jl = operator.length, vi = (int)si * _step + _offset; j < jl; j++, vi += _step) {
					//float sample = (float)samples[vi] * r1 + (float)samples[vi + _step] * r2;
					float sample = (float)samples[vi];
					acc += (sample / maxshort) * operator[j];
				}
				
				output[it] += Math.abs(acc);
				maxvalue = Math.max(maxvalue, output[it]);
			}
			
			synchronized (item) {
				item.maxvalue = Math.max(item.maxvalue, maxvalue);
			}
		}
	}
}
