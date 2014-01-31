package se.embargo.sonar.shader;

import java.nio.ByteBuffer;

import se.embargo.core.graphic.ShaderProgram;
import se.embargo.core.graphic.Shaders;
import se.embargo.sonar.R;
import se.embargo.sonar.Signals;
import se.embargo.sonar.Sonar;
import android.opengl.GLES20;

public class SonogramShader {
    public static final int SHADER_SOURCE_ID = R.raw.sonogram_shader;
	
	private static final String TAG = "SonogramShader";
	
    private final ShaderProgram _program;
    private final int _operatorTexture, _samplesTexture;
    private final int _operatorTextureLocation, _samplesTextureLocation;

	public SonogramShader(ShaderProgram program) {
		_program = program;
		_operatorTextureLocation = _program.getUniformLocation("operator");
		_samplesTextureLocation = _program.getUniformLocation("samples");
		
        // Create the external texture which the camera preview is written to
		int[] textures = new int[2];
        GLES20.glGenTextures(textures.length, textures, 0);

        _operatorTexture = textures[0];
        _samplesTexture = textures[1];
        
        // Setup the operator texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
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

        // Setup the channel0 texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        Shaders.checkGlError("glActiveTexture");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _samplesTexture);
        Shaders.checkGlError("glBindTexture");

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
	}
	
	public void draw(ByteBuffer samples) {
        // Bind the operator texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _operatorTexture);
        GLES20.glUniform1i(_operatorTextureLocation, 0);
        Shaders.checkGlError("glBindTexture");

        // Bind the channel0 texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _samplesTexture);
        GLES20.glUniform1i(_samplesTextureLocation, 1);
        Shaders.checkGlError("glBindTexture");

        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        Shaders.checkGlError("glPixelStorei");
        
        GLES20.glTexImage2D(
        	GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 
        	samples.array().length / 4, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, 
        	samples.position(0));
        Shaders.checkGlError("glTexImage2D");
	}
}
