package se.embargo.sonogram.dsp;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.core.databinding.observable.IObservableValue;
import android.annotation.SuppressLint;

public class SonogramFilter implements ISignalFilter {
	/**
	 * Distance between microphones in meters
	 */
	private final IObservableValue<Float> _baseline;

	private final ReduceBody _body = new ReduceBody();
	
	public SonogramFilter(IObservableValue<Float> baseline) {
		_baseline = baseline;
	}

	@Override
	public void accept(Item item) {
		// Calculate values for each pixel of the canvas
		int length = item.canvas.width() * item.canvas.height();
		if (item.output.length != length) {
			item.output = new float[length];
		}

		item.maxvalue = 0;
		
		// Apply filter in parallel over output rows
		Parallel.forRange(_body, item, 0, item.canvas.height());
	}
	
	private class ReduceBody implements IForBody<Item> {
		@Override
		@SuppressLint("FloatMath")
		public void run(Item item, int y, int ylast) {
			final float[] matched = item.matched;
			final float[] output = item.output;
			final float baseline = _baseline.getValue();
			float maxvalue = 0;
			
			final float topdistance = Signals.distance(item.samplerate, item.window.top - item.resolution.top),
						bottomdistance = Signals.distance(item.samplerate, item.window.bottom - item.resolution.top),
						middistance = Signals.distance(item.samplerate, item.resolution.left - item.window.left + item.resolution.width() / 2);
			final float outputstep = (bottomdistance - topdistance) / (float)item.canvas.height();
			
			// For each line in sonogram
			for (int xlast = item.canvas.width(), oi = y * item.canvas.width(); y < ylast; y++) {
				// Distance to top of window
				final float yd = topdistance + y * outputstep,
						    ysqr = yd * yd;
				
				// For each pixel of current line
				for (int x = 0; x < xlast; x++, oi++) {
					// Distance from mic-A (middle-top of canvas)
					final float xda = middistance - x * outputstep;
					final float ha1 = Signals.sample(item.samplerate, (float)Math.sqrt(xda * xda + ysqr)),
								ha2 = Signals.sample(item.samplerate, (float)Math.sqrt(xda * xda + ysqr) + outputstep);
					final float ra2 = ha1 - (int)ha1, 
								ra1 = 1.0f - ra2;

					// Distance from mic-B (middle-top of canvas)
					final float xdb = xda + baseline;
					final float hb1 = Signals.sample(item.samplerate, (float)Math.sqrt(xdb * xdb + ysqr));
					final float rb2 = hb1 - (int)hb1,
								rb1 = 1.0f - rb2;

					// Reduce the A/B channels 
					float acc = 0;
					final int samplesteps = (int)Math.max(Math.floor(ha2 - ha1), 1.0f);
					
					for (int sai = (int)ha1 * 2, sbi = (int)hb1 * 2 + 1,
						     sal = Math.min(sai + samplesteps, matched.length - 2),
							 sbl = Math.min(sbi + samplesteps, matched.length - 2); 
							 sai < sal && sbi < sbl; sai++, sbi++) { 
						float asample = matched[sai] * ra1 + matched[sai + 2] * ra2;
						float bsample = matched[sbi] * rb1 + matched[sbi + 2] * rb2;
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
