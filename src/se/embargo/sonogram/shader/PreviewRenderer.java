package se.embargo.sonogram.shader;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import se.embargo.core.graphic.ShaderProgram;
import se.embargo.sonogram.dsp.ISignalFilter.Item;
import se.embargo.sonogram.shader.SonogramSurface.Visualization;
import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

public class PreviewRenderer implements GLSurfaceView.Renderer {
    private Context _context;
    private ShaderProgram _program;
    
    private IVisualizationShader _shader;
    private PreviewShader _preview;
    private SonogramSurface.Visualization _visualization = Visualization.Sonogram, _prevVisualization;
    
    private float[] _samples = new float[0], _channel1 = new float[0];
    
    public PreviewRenderer(Context context) {
    	_context = context;
    }

	public synchronized void setVisualization(SonogramSurface.Visualization visualization) {
		_visualization = visualization;
	}
	
	private void createShaderProgram() {
		switch (_visualization) {
			case Sonogram:
				_program = new ShaderProgram(_context, PreviewShader.SHADER_SOURCE_ID, SonogramShader.SHADER_SOURCE_ID);
				_shader = new SonogramShader(_program);
				break;
				
			case Histogram:
				_program = new ShaderProgram(_context, PreviewShader.SHADER_SOURCE_ID, HistogramShader.SHADER_SOURCE_ID);
				_shader = new SonogramShader(_program);
				break;
		}
		
		_prevVisualization = _visualization;
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
    	createShaderProgram();
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        Rect previewSize = new Rect(0, 0, 100, 100);        
    	_preview = new PreviewShader(_program, previewSize, width, height);
    }

    @Override
    public synchronized void onDrawFrame(GL10 glUnused) {
    	if (_visualization != _prevVisualization) {
    		createShaderProgram();
    	}
    	
    	if (_samples != null && _channel1 != null) {
    		_program.draw();
    		_shader.draw(_samples);
    		_preview.draw();
    	}
    }

	public synchronized void receive(Item item) {
		if (_samples.length != item.matched.length) {
			_samples = new float[item.matched.length];
		}
		
		System.arraycopy(item.matched, 0, _samples, 0, item.matched.length);
	}
}
