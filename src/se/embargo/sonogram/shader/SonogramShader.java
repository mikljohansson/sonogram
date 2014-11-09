package se.embargo.sonogram.shader;

import se.embargo.core.graphic.ShaderProgram;
import se.embargo.core.graphic.Shaders;
import se.embargo.sonogram.R;
import android.opengl.GLES20;

public class SonogramShader implements IVisualizationShader {
	private static final String TAG = "SonogramShader";
	
    private final ShaderProgram _program;
    //private final int _operatorTexture, _samplesTexture;
    private int _samplesLocation0, _samplesLocation1;

	public SonogramShader(ShaderProgram program) {
		_program = program;
		_samplesLocation0 = _program.getUniformLocation("samples0");
		_samplesLocation1 = _program.getUniformLocation("samples1");
		
        // Create the external texture which the camera preview is written to
		/*
		int[] textures = new int[1];
        GLES20.glGenTextures(textures.length, textures, 0);

        _samplesTexture = textures[0];
        //_operatorTexture = textures[1];
        
        // Setup the samples texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        Shaders.checkGlError("glActiveTexture");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _samplesTexture);
        Shaders.checkGlError("glBindTexture");

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        */
        // Setup the operator
        /*
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        Shaders.checkGlError("glActiveTexture");
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _operatorTexture);
        Shaders.checkGlError("glBindTexture");

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        Shaders.checkGlError("glPixelStorei");

        ByteBuffer buffer = ByteBuffer.allocateDirect(Sonar.OPERATOR.length * 4);
        buffer.asShortBuffer().put(Signals.toShort(Sonar.OPERATOR, 1, 2));
        
        GLES20.glTexImage2D(
        	GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 
        	buffer.array().length / 4, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, 
        	buffer.position(0));
        Shaders.checkGlError("glTexImage2D");
        */
	}
	
	@Override
	public void draw(float[] samples0, float[] samples1) {
        GLES20.glUniform1fv(_samplesLocation0, Math.min(samples0.length, 512), samples0, 0);
        GLES20.glUniform1fv(_samplesLocation1, Math.min(samples1.length, 512), samples1, 0);
        Shaders.checkGlError("glUniform1fv");

        // Bind the samples texture
        /*
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _samplesTexture);
        GLES20.glUniform1i(_samplesLocation, 0);
        Shaders.checkGlError("glBindTexture");

        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        Shaders.checkGlError("glPixelStorei");
        
        GLES20.glTexImage2D(
        	GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 
        	samples.array().length / 4, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, 
        	samples.position(0));
        Shaders.checkGlError("glTexImage2D");
        */

        // Bind the operator
        /*
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _operatorTexture);
        GLES20.glUniform1i(_operatorTextureLocation, 1);
        Shaders.checkGlError("glBindTexture");
        */
	}
}
