package se.embargo.sonar.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import se.embargo.core.graphic.ShaderProgram;
import se.embargo.sonar.R;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

public class PreviewShader implements IRenderStage {
	public static final int SHADER_SOURCE_ID = R.raw.vertex_shader;    
	
	private static final String TAG = "PreviewShader";
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    private ShaderProgram _program;

    private final float[] _vertices = {
        // X, Y, Z, U, V
    	-1.0f,  1.0f, 0.0f, 0.0f, 1.0f,		// Top left
    	 1.0f,  1.0f, 0.0f, 1.0f, 1.0f,		// Top right
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,		// Bottom left
         1.0f, -1.0f, 0.0f, 1.0f, 0.0f		// Bottom right
    };
    private FloatBuffer _vertexbuf;

    private int muMVPMatrixHandle;
    private int maPositionHandle;
    private int maTextureCoordHandle;
    private int muSTMatrixHandle;
    private int muCRatioHandle;

    private float[] mMVPMatrix = new float[16];
    private float[] mVMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private float[] mProjMatrix = new float[16];

    private float _cRatio;
    
    /**
     * @param program	Shader program built from SHADER_SOURCE_ID
     * @param size		Size of camera preview frames
     */
    public PreviewShader(ShaderProgram program, Rect size, int surfaceWidth, int surfaceHeight) {
    	_program = program;
    	_cRatio = (float)size.width() / size.height();
        
        // Allocate buffer to hold vertices
    	_vertexbuf = ByteBuffer.allocateDirect(_vertices.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        _vertexbuf.put(_vertices).position(0);

        // Find handles to shader parameters
        maPositionHandle = _program.getAttributeLocation("aPosition");
        maTextureCoordHandle = _program.getAttributeLocation("aTextureCoord");
        muMVPMatrixHandle = _program.getUniformLocation("uMVPMatrix");
        muSTMatrixHandle = _program.getUniformLocation("uSTMatrix");
        muCRatioHandle = _program.getUniformLocation("uCRatio");

        // Set the viewpoint
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, 1.45f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        Matrix.setIdentityM(mSTMatrix, 0);

        // Set the screen ratio projection 
        float ratio = (float)surfaceWidth / surfaceHeight;
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 1f, 10);

        // Apply the screen ratio projection
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);
    }
    
    @Override
    public void draw() {
    	// Transfer the screen ratio projection
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glUniform1f(muCRatioHandle, _cRatio);
    
        // Prepare the triangles
        _vertexbuf.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(
        	maPositionHandle, 3, GLES20.GL_FLOAT, false,
        	TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vertexbuf);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        
        _vertexbuf.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(
        	maTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
        	TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vertexbuf);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureCoordHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        // Draw two triangles to form a square
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 1, 3);
        checkGlError("glDrawArrays");
    }
    
	private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
