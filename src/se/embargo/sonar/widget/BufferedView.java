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
	private Rect _canvasWindow;
	private final Paint _bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private boolean _invalid;
	
	private Rect _dataResolution;
	private Rect _zoomWindow;
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
		return _dataResolution;
	}

	public void setResolution(Rect resolution) {
		_dataResolution = new Rect(resolution);
		_zoomWindow = new Rect(_dataResolution);
		_zoomx = _zoomWindow.left;
		_zoomy = _zoomWindow.right;
		_zoomw = _zoomWindow.width();
		_zoomh = _zoomWindow.height();
		setZoom(_zoom);
	}
	
	public void setZoom(float zoom, float x, float y) {
		float zoomw = Math.min((float)_dataResolution.width() / zoom, _dataResolution.width()),
			  zoomh = Math.min((float)_dataResolution.height() / zoom, _dataResolution.height());
		
		float xadj = (zoomw - _zoomw) * (x - _zoomx) / _zoomWindow.width(),
		      yadj = (zoomh - _zoomh) * (y - _zoomy) / _zoomWindow.height();

		float zoomx = Math.max(Math.min(_zoomx - xadj + zoomw, _dataResolution.right) - zoomw, _dataResolution.left),
			  zoomy = Math.max(Math.min(_zoomy - yadj + zoomh, _dataResolution.bottom) - zoomh, _dataResolution.top);
		
		_zoom = Math.max((float)_dataResolution.width() / zoomw, (float)_dataResolution.height() / zoomh);
		_zoomx = zoomx;
		_zoomy = zoomy;
		_zoomw = zoomw;
		_zoomh = zoomh;
		
		_zoomWindow.left = Math.max((int)zoomx, _dataResolution.left);
		_zoomWindow.top = Math.max((int)zoomy, _dataResolution.top);
		_zoomWindow.right = Math.min(_zoomWindow.left + (int)zoomw, _dataResolution.right);
		_zoomWindow.bottom = Math.min(_zoomWindow.top + (int)zoomh, _dataResolution.bottom);
		
		invalidateCanvas();
	}
	
	public void setZoom(float zoom) {
		setZoom(zoom, _zoomx, _zoomy);
	}
	
	private void setPosition(float x, float y) {
		int w = _zoomWindow.width(), h = _zoomWindow.height();
		_zoomx = Math.max(_dataResolution.left, Math.min(x, _dataResolution.right - w));
		_zoomy = Math.max(_dataResolution.top, Math.min(y, _dataResolution.bottom - h));
		
		_zoomWindow.left = (int)_zoomx;
		_zoomWindow.top = (int)_zoomy;
		_zoomWindow.right = _zoomWindow.left + w;
		_zoomWindow.bottom = _zoomWindow.top + h;
		
		invalidateCanvas();
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
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		if (w > 0 && h > 0) {
			if (_bitmap != null) {
				_bitmap.recycle();
				_bitmap = null;
			}
			
			_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
			_canvas = new Canvas(_bitmap);
			_canvasWindow = new Rect(0, 0, w, h);
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
							  zoomy = _zy + (_y - (y - _canvasWindow.top)) * yres;
						setPosition(zoomx, zoomy);
						_x = event.getX();
						_y = event.getY();
						_zx = _zoomx;
						_zy = _zoomy;
						result = true;
					}
					break;
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
