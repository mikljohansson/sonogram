package se.embargo.sonar.dsp;

public interface ISignalFilter {
	public class Item {
		public final float samplerate;
		public final float[] operator;
		public final short[] samples;
		public float[] output;
		
		public Item(float samplerate, float[] operator, int chunksize) {
			this.samplerate = samplerate;
			this.operator = operator;
			this.samples = new short[chunksize];
			this.output = new float[0];
		}
		
		public void init(short[] samples) {
			System.arraycopy(samples, 0, this.samples, 0, samples.length);
		}
	}
	
	void accept(Item item);
}
