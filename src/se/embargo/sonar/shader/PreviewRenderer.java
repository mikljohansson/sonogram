package se.embargo.sonar.shader;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import se.embargo.core.graphic.ShaderProgram;
import se.embargo.sonar.dsp.ISignalFilter.Item;
import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

public class PreviewRenderer implements GLSurfaceView.Renderer {
    private Context _context;
    private ShaderProgram _program;
    
    private SonogramShader _shader;
    private PreviewShader _preview;
    
    private float[] _operator, _channel0 = new float[0], _channel1 = new float[0];
    
    public PreviewRenderer(Context context) {
    	_context = context;
    }
    
    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
    	// Turn off unneeded features 
    	/*
    	GLES20.glDisable(GLES20.GL_BLEND);
    	GLES20.glDisable(GLES20.GL_CULL_FACE);
    	GLES20.glDisable(GLES20.GL_DITHER);
    	GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    	GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    	GLES20.glDisable(GLES20.GL_STENCIL_TEST);
    	GLES20.glDepthMask(false);
    	*/

    	// Background color
    	GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    	
    	_program = new ShaderProgram(_context, PreviewShader.SHADER_SOURCE_ID, SonogramShader.SHADER_SOURCE_ID);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        Rect previewSize = new Rect(0, 0, 100, 100);        
    	_preview = new PreviewShader(_program, previewSize, width, height);
    	_shader = new SonogramShader(_program);
    }

    @Override
    public synchronized void onDrawFrame(GL10 glUnused) {
    	if (_channel0 != null && _channel1 != null) {
    		_program.draw();
    		_shader.draw(_operator, _channel0, _channel1);
    		_preview.draw();
    	}
    }

	public synchronized void receive(Item item) {
		final short[] samples = item.samples;
		_operator = item.operator;
		
		if (_channel0.length != samples.length / 2) {
			_channel0 = new float[samples.length / 2];
		}
		
		if (_channel1.length != samples.length / 2) {
			_channel1 = new float[samples.length / 2];
		}

		for (int i = 0, j = 0; i < samples.length; i += 2, j++) {
			_channel0[j] = (float)samples[i] / Short.MAX_VALUE;
			_channel1[j] = (float)samples[i + 1] / Short.MAX_VALUE;
		}
	}
}
