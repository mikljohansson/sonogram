#extension GL_OES_EGL_image_external : require
precision mediump float;

const int samplerate = 44100;		// Sample rate in Hz
const float baseline = 0.12;		// Distance between microphones in meters
const float speed = 340.29;			// Speed of sound in m/s

const int samplecount = 512;
const float maxsampledistance = float(samplecount);

const float micoffset = (baseline / speed) * (float(samplerate) / maxsampledistance) / 2.0;
const vec2 micoffset0 = vec2(-0.11, -0.5 - micoffset);
const vec2 micoffset1 = vec2(-0.11, -0.5 + micoffset);

uniform float samples0[samplecount];
uniform float samples1[samplecount];

varying vec2 vTextureCoord;

float sumv(vec4 s0) {
	return s0[0] + s0[1] + s0[2] + s0[3];
}

vec4 sam0(int pos0) {
	return vec4(samples0[pos0], samples0[pos0 + 1], samples0[pos0 + 2], samples0[pos0 + 3]);
}

vec4 sam1(int pos1) {
	return vec4(samples1[pos1], samples1[pos1 + 1], samples1[pos1 + 2], samples1[pos1 + 3]);
}

float samplev(int pos0, int pos1) {
	vec4 v00 = sam0(pos0), v01 = sam0(pos0 + 4), v02 = sam0(pos0 + 8);
	vec4 v10 = sam1(pos1), v11 = sam1(pos1 + 4), v12 = sam1(pos1 + 8);
	
	vec4 avg0 = vec4((sumv(v00) + sumv(v01) + sumv(v02)) / 12.0);
	vec4 avg1 = vec4((sumv(v10) + sumv(v11) + sumv(v12)) / 12.0);
	
	return 
		dot(v00 - avg0, v10 - avg1) + 
		dot(v01 - avg0, v11 - avg1) + 
		dot(v02 - avg0, v12 - avg1);
}

void main() {
	// Calculate the distance from this pixel to each of the microphones
	int pos0 = int(length(vTextureCoord + micoffset0) * maxsampledistance);
	int pos1 = int(length(vTextureCoord + micoffset1) * maxsampledistance);
	
	float sample = samplev(pos0, pos1);
	
	float value = log2(log2(abs(sample) + 1.0) + 1.0);
	gl_FragColor = vec4(value, value, value, 1.0);
}
