#extension GL_OES_EGL_image_external : require
precision mediump float;

#define OPERATOR_SIZE 960
#define BUFFER_SIZE 1920

const int samplerate = 48000;		// Sample rate in Hz
const float baseline = 0.120;		// Distance between microphones in meters
const float speed = 340.29;			// Speed of sound in m/s

const float micoffset = (baseline / speed) * (float(samplerate) / float(BUFFER_SIZE)) / 2.0;
const vec2 micoffset0 = vec2(-0.11, -0.5 - micoffset);
const vec2 micoffset1 = vec2(-0.11, -0.5 + micoffset);

uniform float samples0[BUFFER_SIZE];
uniform float samples1[BUFFER_SIZE];
uniform float operator[OPERATOR_SIZE];

varying vec2 vTextureCoord;

void main() {
	// Calculate the distance from this pixel to each of the microphones
	int pos0 = int(length(vTextureCoord + micoffset0) * float(BUFFER_SIZE));
	int pos1 = int(length(vTextureCoord + micoffset1) * float(BUFFER_SIZE));
	
	float sample = 0.0;
	for (int i = 0; i < OPERATOR_SIZE; i++) {
		sample += samples0[pos0 + i] * samples1[pos1 + i] * operator[i];
	}
	
	float value = log2(log2(abs(sample) + 1.0) + 1.0);
	gl_FragColor = vec4(value, value, value, 1.0);
}
