package se.embargo.sonar.shader;

import se.embargo.sonar.dsp.ISignalFilter;
import se.embargo.sonar.io.ISonarController;
import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class SonogramSurface extends GLSurfaceView implements ISonarController, ISignalFilter {
	private final PreviewRenderer _renderer;
	private Rect _resolution;

	public SonogramSurface(Context context, AttributeSet attrs) {
		super(context, attrs);
		setEGLContextClientVersion(2);

		_renderer = new PreviewRenderer(context);
		setRenderer(_renderer);
	}

	public SonogramSurface(Context context) {
		this(context, null);
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
