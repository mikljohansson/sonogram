package se.embargo.sonar.test;

import java.util.Random;

import android.test.AndroidTestCase;

import se.embargo.sonar.Signals;

public class SignalsTest extends AndroidTestCase {
	public void testDynamicProgrammingConvolve() {
		int samplerate = 4410;
		int pulseoffset = 50;
		
		// Create noise array
		short[] data = new short[samplerate];
		Random rand = new Random(1);
		for (int i = 0; i < data.length; i++) {
			data[i] = (short)(rand.nextInt() % (Short.MAX_VALUE / 25));
		}

		// Add pulse into noise array
		double[] pulse = Signals.createLinearChirp(samplerate, 20, 0.75, samplerate / 10, samplerate / 2 - samplerate / 10);
		for (int i = 0; i < pulse.length; i++) {
			data[i + pulseoffset] = (short)Math.max(Short.MIN_VALUE, Math.min((double)data[i + pulseoffset] + pulse[i] * Short.MAX_VALUE / 10, Short.MAX_VALUE));
		}
		
		double[] output = new double[data.length];
 		Signals.convolve(data, pulse, output);
		Signals.detect(output);
		
		assertTrue(output[pulseoffset] > 0);
		assertEquals(0.0d, output[pulseoffset - pulseoffset / 2]);
		assertEquals(0.0d, output[pulseoffset + pulseoffset / 2]);
	}
}
