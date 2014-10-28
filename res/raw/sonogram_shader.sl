#extension GL_OES_EGL_image_external : require
precision mediump float;

const int samplerate = 44100;		// Sample rate in Hz
const int interval = 80;			// Ping interval in milliseconds
const int pulserange = 15;			// Pulse range in milliseconds
const int pulselength = 1;			// Pulse length in milliseconds
const float baseline = 0.12;		// Distance between microphones in meters
const float speed = 340.29;			// Speed of sound in m/s

const int samplecount = samplerate * interval / 1000;
const int rangecount = samplerate * pulserange / 1000;
const int operatorcount = samplerate * pulselength / 1000;
const float maxsampledistance = float(rangecount);

const float micoffset = (baseline / speed) * (float(samplerate) / maxsampledistance) / 2.0;
const vec2 micoffset0 = vec2(-0.11, -0.5 - micoffset);
const vec2 micoffset1 = vec2(-0.11, -0.5 + micoffset);

uniform float samples[(samplecount + operatorcount) * 2];

varying vec2 vTextureCoord;

void main() {
	// Calculate the distance from this pixel to each of the microphones
	int pos0 = int(length(vTextureCoord + micoffset0) * maxsampledistance) * 2;
	int pos1 = int(length(vTextureCoord + micoffset1) * maxsampledistance) * 2 + 1;
	float value = log2(log2(samples[pos0] * samples[pos1] + 1.0) + 1.0);
	gl_FragColor = vec4(value, value, value, 1.0);
}
