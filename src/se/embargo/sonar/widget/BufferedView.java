package se.embargo.sonar.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public abstract class BufferedView extends View {
	private Bitmap _bitmap;
	private Canvas _canvas;
	private volatile Rect _canvasWindow = new Rect(0, 0, 100, 100);
	private final Paint _bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private boolean _invalid;
	
	private volatile Rect _resolution = new Rect(_canvasWindow);
	private volatile Rect _zoomWindow = new Rect(_canvasWindow);
	private float _zoom = 1.0f;
	private float _zoomx = 0, _zoomy = 0, _zoomw = 0, _zoomh = 0;

	/**
	 * True if a single finger is touching the screen.
	 */
	private boolean _singleTouch = false;
	
	public BufferedView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setOnTouchListener(new GestureDetector());
	}

	public BufferedView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public BufferedView(Context context) {
		this(context, null);
	}

	protected abstract void draw(Canvas canvas, Rect dataWindow, Rect canvasWindow);
	
	public Rect getResolution() {
		return _resolution;
	}

	public synchronized void setResolution(Rect resolution) {
		_resolution = new Rect(resolution);
		_zoomw = (float)_resolution.width() / _zoom;
		_zoomh = (float)_resolution.height() / _zoom;
		_zoomx = _resolution.centerX() - _zoomw / 2;
		_zoomy = _resolution.top;
		
		setWindow(new Rect(
			Math.max((int)_zoomx, _resolution.left),
			Math.max((int)_zoomy, _resolution.top),
			Math.min((int)_zoomx + (int)_zoomw, _resolution.right),
			Math.min((int)_zoomy + (int)_zoomh, _resolution.bottom)));
	}
	
	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	public Rect getWindow() {
		return _zoomWindow;
	}
	
	protected synchronized void setWindow(Rect window) {
		_zoomWindow = window;
		
		post(new Runnable() {
			@Override
			public void run() {
				invalidateCanvas();
			}
		});
	}
	
	/**
	 * @note	Must not lock or the audio reader thread will be blocked
	 */
	public Rect getCanvas() {
		return _canvasWindow;
	}
	
	protected synchronized void setCanvas(Rect canvas) {
		_canvasWindow = canvas;
	}
	
	/**
	 * @param zoom	Zoom factor to apply
	 * @param x		Coordinate in data window to focus zoom on
	 * @param y		Coordinate in data window to focus zoom on
	 */
	public synchronized void setZoom(float zoom, float x, float y) {
		float zoomw = Math.min((float)_resolution.width() / zoom, _resolution.width()),
			  zoomh = Math.min((float)_resolution.height() / zoom, _resolution.height());
		
		float xadj = (zoomw - _zoomw) * (x - _zoomx) / _zoomWindow.width(),
		      yadj = (zoomh - _zoomh) * (y - _zoomy) / _zoomWindow.height();

		float zoomx = Math.max(Math.min(_zoomx - xadj + zoomw, _resolution.right) - zoomw, _resolution.left),
			  zoomy = Math.max(Math.min(_zoomy - yadj + zoomh, _resolution.bottom) - zoomh, _resolution.top);
		
		_zoom = Math.max((float)_resolution.width() / zoomw, (float)_resolution.height() / zoomh);
		_zoomx = zoomx;
		_zoomy = zoomy;
		_zoomw = zoomw;
		_zoomh = zoomh;

		setWindow(new Rect(
			Math.max((int)_zoomx, _resolution.left),
			Math.max((int)_zoomy, _resolution.top),
			Math.min((int)_zoomx + (int)_zoomw, _resolution.right),
			Math.min((int)_zoomy + (int)_zoomh, _resolution.bottom)));
	}
	
	public synchronized void setZoom(float zoom) {
		setZoom(zoom, _zoomx, _zoomy);
	}
	
	private synchronized void setPosition(float x, float y) {
		int w = _zoomWindow.width(), h = _zoomWindow.height();
		_zoomx = Math.max(_resolution.left, Math.min(x, _resolution.right - w));
		_zoomy = Math.max(_resolution.top, Math.min(y, _resolution.bottom - h));
		
		setWindow(new Rect(
			(int)_zoomx, 
			(int)_zoomy,
			(int)_zoomx + w, 
			(int)_zoomy + h));
	}
	
	protected synchronized void invalidateCanvas() {
		_invalid = true;
		invalidate();
	}

	protected synchronized void postInvalidateCanvas() {
		_invalid = true;
		postInvalidate();
	}
	
	@Override
	protected synchronized void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		if (w > 0 && h > 0) {
			if (_bitmap != null) {
				_bitmap.recycle();
				_bitmap = null;
			}
			
			_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
			_canvas = new Canvas(_bitmap);
			setCanvas(new Rect(0, 0, w, h));
		}
	}
	
	@Override
	protected synchronized void onDraw(Canvas canvas) {
	    super.onDraw(canvas);

	    if (_invalid) {
	    	_invalid = false;
	    	_canvas.drawColor(Color.BLACK);
	    	draw(_canvas, _zoomWindow, _canvasWindow);
	    }
	
	    if (_bitmap != null) {
	    	canvas.drawBitmap(_bitmap, 0, 0, _bitmapPaint);
	    }
	}
	
	private class GestureDetector extends ScaleGestureDetector implements OnTouchListener {
		private float _x = 0, _y = 0, _zx = 0, _zy = 0;
		
		public GestureDetector() {
			super(getContext(), new ScaleListener());
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			boolean result = super.onTouchEvent(event);
			
			synchronized (BufferedView.this) {
				switch (event.getActionMasked()) {
					case MotionEvent.ACTION_CANCEL:
						_singleTouch = false;
						result = true;
						break;
	
					case MotionEvent.ACTION_UP:
						_singleTouch = false;
						result = true;
						break;
	
					case MotionEvent.ACTION_DOWN:
						_singleTouch = true;
						_x = event.getX();
						_y = event.getY();
						_zx = _zoomx;
						_zy = _zoomy;
						result = true;
						break;
						
					case MotionEvent.ACTION_MOVE:
						float x = event.getX(), y = event.getY();
						if (_singleTouch) {
							float xres = (float)_zoomWindow.width() / (float)_canvasWindow.width(),
								  yres = (float)_zoomWindow.height() / (float)_canvasWindow.height();
							float zoomx = _zx + (_x - (x - _canvasWindow.left)) * xres, 
								  zoomy = _zy - (_y - (y - _canvasWindow.top)) * yres;
							setPosition(zoomx, zoomy);
							_x = event.getX();
							_y = event.getY();
							_zx = _zoomx;
							_zy = _zoomy;
							result = true;
						}
						break;
				}
			}
			
			return result;
		}
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			_singleTouch = false;

			float xres = (float)_zoomWindow.width() / (float)_canvasWindow.width(),
				  yres = (float)_zoomWindow.height() / (float)_canvasWindow.height();
			setZoom(_zoom * detector.getScaleFactor(), 
				    _zoomx + (detector.getFocusX() - _canvasWindow.left) * xres, 
				    _zoomy + (detector.getFocusY() - _canvasWindow.top) * yres);
			return true;
		}
	}
}
