#extension GL_OES_EGL_image_external : require
precision mediump float;

const int samplerate = 48000;		// Sample rate in Hz
const int samplecount = 256;

uniform float samples0[samplecount];
uniform float samples1[samplecount];

varying vec2 vTextureCoord;

void main() {
	int pos = int(float(samplecount) * vTextureCoord.y);
	
	float upper = step(0.5, vTextureCoord.x);
	float lower = step(vTextureCoord.x, 0.5);
	float sample = abs(samples0[pos] * upper + samples1[pos] * lower);
	
	float value = log2(log2(sample + 1.0) + 1.0);
	float color = step((vTextureCoord.x - 0.5 * upper) * 2.0, value * 0.9 + 0.23 * lower);
	
	gl_FragColor = vec4(color, color, color, 1.0);
}
