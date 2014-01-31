#extension GL_OES_EGL_image_external : require
precision mediump float;

const int samplerate = 44100;		// Sample rate in Hz
const int interval = 60;			// Ping interval in milliseconds
const int pulselength = 20;			// Pulse length in milliseconds
const float baseline = 0.12;		// Distance between microphones in meters
const float speed = 340.29;			// Speed of sound in m/s

const int samplecount = samplerate * interval / 1000;
const int operatorcount = samplerate * pulselength / 1000;
const float foperatorcount = float(operatorcount);

const float samplestep = 1.0 / float(samplecount);
const float operatorstep = 1.0 / foperatorcount;
const float operatorstart = operatorstep / 2.0;

const float micoffset = baseline / speed / 2.0;
const vec4 sampleadjust = vec4(0.5, 0.5, 0.5, 0.5);

uniform sampler2D operator;
uniform sampler2D samples;

varying vec2 vTextureCoord;

void main() {
	// Calculate the distance from this pixel to each of the microphones
	vec2 pos0 = vec2(length(vec2(vTextureCoord.x + micoffset, vTextureCoord.y)), 0.0);
	vec2 pos1 = vec2(length(vec2(vTextureCoord.x - micoffset, vTextureCoord.y)), 0.0);
	vec2 opos = vec2(operatorstart, 0.0);
	float acc = 0.0;
	
	for (int i = 0; i < operatorcount; i++) {
		// Sample the operator
		vec4 osample = texture2D(operator, opos);
	
		// Sample the channels
		vec4 sample0 = texture2D(samples, pos0) - sampleadjust;
		vec4 sample1 = texture2D(samples, pos1) - sampleadjust;
		
		// Accumulate these samples
		// TODO: byte order (swap order of bytes)
		//acc += (sample0[1] + sample0[0] / 256.0) * (sample1[3] + sample1[2] / 256.0) * (osample[1] + osample[0] / 256.0);
		acc += (sample0[0] + sample0[1] / 256.0) * (sample1[2] + sample1[3] / 256.0) * (osample[0] + osample[1] / 256.0);
		
		// Step to next sample positions
		opos.x += operatorstep;
		pos0.x += samplestep;
		pos1.x += samplestep;
	}
	
	// Keep the value in [0.0, 1.0]
	acc /= foperatorcount;
	gl_FragColor = vec4(acc, acc, acc, 1.0);
}
