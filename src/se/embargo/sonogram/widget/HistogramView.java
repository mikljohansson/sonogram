package se.embargo.sonogram.widget;

import se.embargo.sonogram.dsp.ISignalFilter;
import se.embargo.sonogram.io.ISonarController;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

public class HistogramView extends BufferedView implements ISonarController, ISignalFilter {
	private final Paint _outline;
	private float[] _values;
	private float _maxvalue;

	public HistogramView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		
		_outline = new Paint();
		_outline.setColor(Color.WHITE);
	}

	public HistogramView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public HistogramView(Context context) {
		this(context, null);
	}
	
	@Override
	public synchronized void setResolution(Rect resolution) {
		super.setResolution(resolution);
		_values = new float[resolution.width()];
	}
	
	@Override
	public synchronized void setSonarResolution(Rect resolution) {
		setResolution(resolution);
	}

	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	@Override
	public Rect getSonarWindow() {
		return getWindow();
	}
	
	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	@Override
	public Rect getSonarCanvas() {
		return getCanvas();
	}
	
	@Override
	public synchronized void accept(ISignalFilter.Item item) {
		System.arraycopy(item.output, 0, _values, 0, item.output.length);
		_maxvalue = item.maxvalue;
		postInvalidateCanvas();
	}

	/*
	@Override
	protected synchronized void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		if (w > 0 && h > 0) {
			Rect resolution = getResolution();
			setResolution(new Rect(resolution.left, resolution.top, resolution.right, h));
		}
	}
	*/

	@Override
	protected synchronized void draw(Canvas canvas, Rect dataWindow, Rect canvasWindow) {
    	Rect resolution = getResolution();
	    
	    // Draw the histogram
	    float scale = (float)canvasWindow.height() / Math.min(dataWindow.height(), resolution.height());
	    float top = (float)(dataWindow.top - resolution.top) * scale;
	    float factor = (float)resolution.height() * scale / (float)Math.log(_maxvalue + 1);
	    //float factor = (float)resolution.height() * scale / _maxvalue;
	    
	    for (int i = canvasWindow.left, j = 0; i < canvasWindow.right && j < _values.length; i++, j++) {
	    	// Scale the value logarithmically into the maximum height
	    	float value = factor * (float)Math.log(Math.abs(_values[j]) + 1) - top;
	    	//float value = factor * Math.abs(_values[j]) - bottom;
	    	canvas.drawLine(i, canvasWindow.bottom, i, canvasWindow.bottom - value, _outline);
	    }
	}
}
