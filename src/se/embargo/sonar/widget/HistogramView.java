package se.embargo.sonar.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

public class HistogramView extends BufferedView {
	private final Paint _outline;
	private int _width = 100, _height = 100;
	
	private double[] _values = new double[_width];

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
	
	public void setResolution(int buckets) {
		setResolution(new Rect(0, 0, buckets, _height));
		_values = new double[buckets];
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		if (w > 0 && h > 0) {
			Rect resolution = getResolution();
			setResolution(new Rect(resolution.left, resolution.top, resolution.right, h));
		}
	}
	
	public synchronized void update(int x, double[] values) {
		for (int i = 0; i < values.length; i++) {
			_values[x + i] *= 0.75;
			_values[x + i] += Math.abs(values[i]);
		}
		
		postInvalidateCanvas();
	}
	
	@Override
	protected synchronized void draw(Canvas canvas, Rect dataWindow, Rect canvasWindow) {
	    double canvasHeight = (float)(canvasWindow.bottom - canvasWindow.top) * 0.95f;
	    double step = (float)(canvasWindow.right - canvasWindow.left) / (float)(dataWindow.right - dataWindow.left);
	    double maxval = 0;

	    {
	    	double accumulator = 0;
	    	double x = canvasWindow.left;
		    int j = (int)x, prev = j;
	
		    // Find the maximum value in data window
		    for (int i = dataWindow.left; i < dataWindow.right; i++, x += step) {
		    	accumulator += Math.abs(_values[i]);
		    	
		    	if ((int)x != prev) {
	    	    	if (accumulator > maxval) {
	    	    		maxval = accumulator;
	    	    	}
		    	}
		    }
	    }
	    
	    // Draw the histogram
	    {
	    	double accumulator = 0;
	    	double x = canvasWindow.left;
		    int j = (int)x, prev = j;

		    for (int i = dataWindow.left; i < dataWindow.right; i++, x += step) {
		    	accumulator += Math.abs(_values[i]);
		    	
		    	if ((int)x != prev) {
			    	// Scale the value logarithmically into the maximum height
			    	float value = (float)Math.floor(canvasHeight * Math.log(accumulator + 1) / Math.log(maxval + 1));
			    	//float value = (float)(_values[i] * ((double)_height / maxval));
			    	canvas.drawLine((int)x, canvasWindow.bottom, (int)x, canvasWindow.bottom - value, _outline);
			    	
			    	prev = (int)x;
			    	accumulator = 0;
		    	}
		    }
		    
		    // TODO: draw last remaining accumulator value
	    }
	}
}
