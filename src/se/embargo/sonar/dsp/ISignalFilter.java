package se.embargo.sonar.dsp;

import android.graphics.Rect;


public interface ISignalFilter {
	public class Item {
		public final float samplerate;
		public final short[] samples;
		public Rect window, canvas;
		public float[] output;
		public float maxvalue;
		
		public Item(float samplerate, int samplecount) {
			this.samplerate = samplerate;
			this.samples = new short[samplecount];
			this.output = new float[0];
		}
		
		public void init(short[] samples, Rect window, Rect canvas) {
			System.arraycopy(samples, 0, this.samples, 0, samples.length);
			this.window = window;
			this.canvas = canvas;
		}
	}
	
	void accept(Item item);
}
