package se.embargo.sonar.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

public class HistogramView extends BufferedView {
	private final Paint _outline;
	
	private int _maxgenerations = 4, _generation = 0, _valuecount = 0;
	private float[] _history;
	private float[] _accumulators;
	private int _resolution;

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
	
	public synchronized void setResolution(int buckets) {
		setResolution(new Rect(0, 0, buckets, getResolution() != null ? getResolution().bottom : 100));
		_history = new float[buckets * _maxgenerations];
		_resolution = getResolution().width();
	}
	
	@Override
	protected synchronized void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		if (w > 0 && h > 0) {
			Rect resolution = getResolution();
			setResolution(new Rect(resolution.left, resolution.top, resolution.right, h));
		}
	}
	
	public void update(int x, float[] values) {
		int xg = x + _resolution * _generation;
		System.arraycopy(values, 0, _history, xg, values.length);
		
		synchronized (this) {
			_valuecount += values.length;
			if (_valuecount >= _resolution) {
				_generation = (_generation + 1) % _maxgenerations;
				_valuecount = 0;
				postInvalidateCanvas();
			}
		}
	}
	
	@Override
	protected synchronized void draw(Canvas canvas, Rect dataWindow, Rect canvasWindow) {
    	Rect resolution = getResolution();
	    float step = (float)canvasWindow.width() / (float)dataWindow.width();
	    float maxval = 0;
	    int maxpos = 0;

	    {
	    	float x = canvasWindow.left;
		    int prev = (int)x;
		    
		    // Allocate a buffer to hold the accumulators
		    int accn = canvasWindow.width() * _resolution / dataWindow.width() + 1;
		    if (_accumulators == null || _accumulators.length != accn) {
		    	_accumulators = new float[accn];
		    }
	
		    // Accumulate values for each data point
		    for (int i = 0, xi = (int)x; i < _resolution && xi < accn; i++, xi = (int)x) {
		    	_accumulators[xi] = 0;
		    	
		    	for (int j = 0; j < _maxgenerations; j++) {
		    		_accumulators[xi] += Math.abs(_history[j * _resolution + i]);
		    	}
		    	
		    	x += step;
		    	if ((int)x != prev) {
	    	    	if (_accumulators[xi] > maxval) {
	    	    		maxval = _accumulators[xi];
	    	    		maxpos = xi;
	    	    	}
	    	    	
	    	    	prev = (int)x;
		    	}
		    }
	    }
	    
	    // Draw the histogram
	    {
		    float scale = (float)canvasWindow.height() / (float)dataWindow.height();
		    float bottom = (float)(resolution.bottom - dataWindow.bottom) * scale;
		    //float factor = (float)resolution.height() * scale / (float)Math.log(maxval + 1);
		    float factor = (float)resolution.height() * scale / maxval;
		    
		    for (int i = canvasWindow.left, j = _accumulators.length * dataWindow.left / _resolution; i < canvasWindow.right && j < _accumulators.length; i++, j++) {
		    	// Scale the value logarithmically into the maximum height
		    	float value = _accumulators[((maxpos + j) % _accumulators.length)];
		    	//value = (float)Math.floor(factor * Math.log(value + 1)) - bottom;
		    	value = factor * value - bottom;
		    	canvas.drawLine(i, canvasWindow.bottom, i, canvasWindow.bottom - value, _outline);
		    }
	    }
	}
}
