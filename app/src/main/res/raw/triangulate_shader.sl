#extension GL_OES_EGL_image_external : require
precision mediump float;

const int samplerate = 48000;		// Sample rate in Hz
const float baseline = 0.143;		// Distance between microphones in meters
const float speed = 340.29;			// Speed of sound in m/s

const int samplecount = 256;
const float maxsampledistance = float(samplecount);

const float micoffset = (baseline / speed) * (float(samplerate) / maxsampledistance) / 2.0;
const vec2 micoffset0 = vec2(-0.11, -0.5 - micoffset);
const vec2 micoffset1 = vec2(-0.11, -0.5 + micoffset);

uniform float samples0[samplecount];
uniform float samples1[samplecount];

varying vec2 vTextureCoord;

void main() {
	// Calculate the distance from this pixel to each of the microphones
	int pos0 = int(length(vTextureCoord + micoffset0) * maxsampledistance);
	int pos1 = int(length(vTextureCoord + micoffset1) * maxsampledistance);
	
	float sample0 = abs(samples0[pos0]);
	float sample1 = abs(samples1[pos1]);
	
	float value = log2(log2(sample0 * sample1 + 1.0) + 1.0);
	
	gl_FragColor = vec4(value, value, value, 1.0);
}
