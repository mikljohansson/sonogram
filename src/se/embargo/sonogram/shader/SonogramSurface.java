package se.embargo.sonogram.shader;

import se.embargo.sonogram.dsp.ISignalFilter;
import se.embargo.sonogram.io.ISonarController;
import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class SonogramSurface extends GLSurfaceView implements ISonarController, ISignalFilter {
	private final PreviewRenderer _renderer;
	private Rect _resolution;

	public enum Visualization { Sonogram, SonogramWavelet, Histogram };
	
	public SonogramSurface(Context context, AttributeSet attrs) {
		super(context, attrs);
		setEGLContextClientVersion(2);

		_renderer = new PreviewRenderer(context);
		setRenderer(_renderer);
	}

	public SonogramSurface(Context context) {
		this(context, null);
	}
	
	public void setVisualization(Visualization visualization) {
		_renderer.setVisualization(visualization);
	}

	@Override
	public void setSonarResolution(Rect resolution) {
		_resolution = resolution;
	}

	@Override
	public Rect getSonarWindow() {
		return _resolution;
	}

	@Override
	public Rect getSonarCanvas() {
		return _resolution;
	}

	@Override
	public void accept(ISignalFilter.Item item) {
		_renderer.receive(item);
	}
}
