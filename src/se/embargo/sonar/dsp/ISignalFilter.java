package se.embargo.sonar.dsp;

import android.graphics.Rect;

public interface ISignalFilter {
	public class Item {
		public final float samplerate;
		public float[] operator;
		public final short[] samples;
		public Rect window, canvas, resolution;
		public float[] matched;
		public float[] output;
		public float maxvalue;
		
		public Item(float samplerate, int samplecount) {
			this.samplerate = samplerate;
			this.samples = new short[samplecount];
			this.matched = new float[samplecount];
			this.output = new float[0];
		}
		
		public void init(float[] operator, short[] samples, Rect window, Rect canvas, Rect resolution) {
			this.operator = operator;
			System.arraycopy(samples, 0, this.samples, 0, samples.length);
			this.window = window;
			this.canvas = canvas;
			this.resolution = resolution;
		}
	}
	
	void accept(Item item);
}
