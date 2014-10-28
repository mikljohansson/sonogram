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

uniform float samples[(samplecount + operatorcount) * 2];

varying vec2 vTextureCoord;

void main() {
	int pos = int(float(samplecount) * vTextureCoord.y) * 2;
	float upper = step(0.5, vTextureCoord.x);
	float sample = log2(log2(samples[pos + int(upper)] + 1.0) + 1.0);
	float value = step((vTextureCoord.x - 0.5 * upper) * 2.0, sample);
	gl_FragColor = vec4(value, value, value, 1.0);
}
