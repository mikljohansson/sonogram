package se.embargo.sonar.dsp;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import android.annotation.SuppressLint;
import android.util.FloatMath;

public class SonogramFilter implements ISignalFilter {
	/**
	 * Distance between microphones in meters
	 */
	private static final float BASELINE = 0.12f;
	
	/**
	 * Speed of sound in meters/second
	 */
	private static final float SPEED = 340.29f;
	
	private final float[] _operator;
	private final FilterBody _filter = new FilterBody();
	private final ReduceBody _reduce = new ReduceBody();
	
	public SonogramFilter(float[] operator) {
		_operator = operator;
	}

	@Override
	public void accept(Item item) {
		// Calculate values for each pixel of the canvas
		int length = item.canvas.width() * item.canvas.height();
		if (item.output.length != length) {
			item.output = new float[length];
		}

		item.maxvalue = 0;
		
		// Convolve both channels
		Parallel.forRange(_filter, item, 0, item.samples.length - _operator.length * 2);
		
		// Apply filter in parallel over output rows
		Parallel.forRange(_reduce, item, 0, item.canvas.height());
	}
	
	private class FilterBody implements IForBody<Item> {
		@Override
		@SuppressLint("FloatMath")
		public void run(Item item, int i, int last) {
			final float[] operator = _operator;
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
	
	private class ReduceBody implements IForBody<Item> {
		@Override
		@SuppressLint("FloatMath")
		public void run(Item item, int y, int ylast) {
			final float[] matched = item.matched;
			final float[] output = item.output;
			float maxvalue = 0;
			
			// Number of samples per pixel on x/y axes
			final float xstep = (float)item.window.width() / (float)item.canvas.width(),
					    ystep = (float)item.window.height() / (float)item.canvas.height();
			
			// Number of samples between microphones
			final float baseline = item.samplerate / SPEED * BASELINE;
			
			// For each line in sonogram
			for (; y < ylast; y++) {
				// Distance in samples to bottom of sonar resolution
				final float ys = ((float)item.canvas.height() - y) * ystep + item.window.top + (item.resolution.height() - item.window.bottom), 
						    ysqr = ys * ys;
				
				// For each pixel of current line
				for (int x = 0, xlast = item.canvas.width(), oi = y * item.canvas.width(); x < xlast; x++, oi++) {
					// Distance in samples from mic-A (middle-bottom of canvas)
					final float xsa = Math.abs(((float)item.canvas.width() - x) * xstep + item.window.left - item.resolution.width() / 2);
					final float ha1 = FloatMath.sqrt(xsa * xsa + ysqr);
					final float ra2 = ha1 - (int)ha1, ra1 = 1.0f - ra2;

					// Distance in samples from mic-B (middle-bottom of canvas)
					final float xsb = xsa + baseline;
					final float hb1 = FloatMath.sqrt(xsb * xsb + ysqr);
					final float rb2 = hb1 - (int)hb1, rb1 = 1.0f - rb2;

					// Reduce the A/B channels 
					float acc = 0;
					for (int sai = (int)ha1 * 2, sbi = (int)hb1 * 2 + 1, 
						     sal = Math.min(sai + (int)Math.ceil(xstep), matched.length - 2),
							 sbl = Math.min(sbi + (int)Math.ceil(xstep), matched.length - 2), 
							 ix = 0, ixl = (int)Math.ceil(xstep) - 1; sai < sal && sbi < sbl; sai++, sbi++, ix++) { 
						float asample;
						float bsample;
						
						if (ix == 0) {						
							asample = matched[sai] * ra1 + matched[sai + 2] * ra2;
							bsample = matched[sbi] * rb1 + matched[sbi + 2] * rb2;
						}
						else if (ix == ixl) {
							asample = matched[sai] * (1.0f - ra1) + matched[sai + 2] * (1.0f - ra2);
							bsample = matched[sbi] * (1.0f - rb1) + matched[sbi + 2] * (1.0f - rb2);
 						}
						else {
							asample = matched[sai] + matched[sai + 2];
							bsample = matched[sbi] + matched[sbi + 2];
						}
						
						acc += asample * bsample;
					}
					
					output[oi] = acc;
					maxvalue = Math.max(maxvalue, acc);
				}
			}
			
			synchronized (item) {
				item.maxvalue = Math.max(item.maxvalue, maxvalue);
			}
		}
	}
}
