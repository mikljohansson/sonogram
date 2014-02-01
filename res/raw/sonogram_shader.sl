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

const vec2 samplestep = vec2(1.0 / float(samplecount), 0.0);
const vec2 operatorstep = vec2(1.0 / foperatorcount, 0.0);
const vec2 operatorstart = vec2(operatorstep.x / 2.0, 0.0);

const float micoffset = baseline / speed / 2.0;
const vec2 micoffset0 = vec2(micoffset, -0.5);
const vec2 micoffset1 = vec2(-micoffset, -0.5);

const vec4 samplemul = vec4(0.00390625, 1.0, 0.00390625, 1.0);
const vec4 sampleadd = vec4(-0.5, -0.5, -0.5, -0.5) * samplemul;

uniform sampler2D samples;
uniform float operator[operatorcount];

varying vec2 vTextureCoord;

void main() {
	// Calculate the distance from this pixel to each of the microphones
	vec2 pos0 = vec2(length(vTextureCoord + micoffset0), 0.5);
	vec2 pos1 = vec2(length(vTextureCoord + micoffset1), 0.5);
	float acc = 0.0;
	
	for (int i = 0; i < operatorcount; i++) {
		// Sample the channels
		vec4 sample = vec4(texture2D(samples, pos0).xy, texture2D(samples, pos1).zw) * samplemul + sampleadd;
		
		// Accumulate these samples
		acc += (sample[0] + sample[1]) * (sample[2] + sample[3]) * operator[i];
		
		// Step to next sample positions
		pos0 += samplestep;
		pos1 += samplestep;
	}
	
	// Keep the value in [0.0, 1.0]
	//acc /= foperatorcount;
	acc += 0.5;
	gl_FragColor = vec4(acc, acc, acc, 1.0);
}
