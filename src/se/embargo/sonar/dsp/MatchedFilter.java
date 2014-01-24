package se.embargo.sonar.dsp;

import se.embargo.core.concurrent.IForBody;

public class MatchedFilter implements ISignalFilter {
	private FilterBody _body = new FilterBody();
	
	@Override
	public void accept(Item item) {
		//Parallel.forRange(_body, item, 0, Math.min(item.samples.length, item.output.length));
		_body.run(item, 0, Math.min(item.samples.length, item.output.length));
	}
	
	private static class FilterBody implements IForBody<Item> {
		@Override
		public void run(Item item, int it, int last) {
			final float[] operator = item.operator;
			final short[] samples = item.samples;
			final float[] output = item.output;
			final float maxshort = Short.MAX_VALUE;
			float accumulator;
			
			for (int i = 0, il = samples.length - operator.length; i < il; i++) {
				accumulator = 0;
				for (int j = operator.length - 1; j >= 0; j--) {
					accumulator += ((float)samples[i + j] / maxshort) * operator[j];
				}
				
				output[i] = accumulator;
			}
		}
	}
}
