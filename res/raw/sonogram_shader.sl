#extension GL_OES_EGL_image_external : require
precision mediump float;

const int samplerate = 44100;		// Sample rate in Hz
const int interval = 80;			// Ping interval in milliseconds
const int pulserange = 15;			// Pulse range in milliseconds
const int pulselength = 15;			// Pulse length in milliseconds
const float baseline = 0.12;		// Distance between microphones in meters
const float speed = 340.29;			// Speed of sound in m/s

const int samplecount = samplerate * interval / 1000;
const int rangecount = samplerate * pulserange / 1000;
const int operatorcount = samplerate * pulselength / 1000;
const float maxsampledistance = float(rangecount) * 2.0;

const float micoffset = (baseline / speed) * (float(samplerate) / maxsampledistance) / 2.0;
const vec2 micoffset0 = vec2(-0.2, -0.5 - micoffset);
const vec2 micoffset1 = vec2(-0.2, -0.5 + micoffset);

uniform float channel0[samplecount + operatorcount];
uniform float channel1[samplecount + operatorcount];
uniform float operator[operatorcount];

varying vec2 vTextureCoord;

void main() {
	// Calculate the distance from this pixel to each of the microphones
	int pos0 = int(length(vTextureCoord + micoffset0) * maxsampledistance);
	int pos1 = int(length(vTextureCoord + micoffset1) * maxsampledistance);
	float acc = 0.0;
	
	for (int i = 0; i < operatorcount; i++) {
		acc += dot(channel0[pos0 + i] * channel1[pos1 + i], operator[i]);
	}
	
	// Keep the value in [0.0, 1.0]
	//acc /= float(operatorcount);
	acc = acc * 100.0 + 0.5;
	gl_FragColor = vec4(acc, acc, acc, 1.0);
}
