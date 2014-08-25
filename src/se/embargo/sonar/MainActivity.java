package se.embargo.sonar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.sonar.dsp.AverageFilter;
import se.embargo.sonar.dsp.CompositeFilter;
import se.embargo.sonar.dsp.FramerateCounter;
import se.embargo.sonar.dsp.ISignalFilter;
import se.embargo.sonar.dsp.MatchedFilter;
import se.embargo.sonar.dsp.SonogramFilter;
import se.embargo.sonar.io.ISonar;
import se.embargo.sonar.io.Sonar;
import se.embargo.sonar.io.StreamReader;
import se.embargo.sonar.io.StreamWriter;
import se.embargo.sonar.shader.SonogramSurface;
import se.embargo.sonar.widget.HistogramView;
import se.embargo.sonar.widget.SonogramView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockFragmentActivity {
	private static final String TAG = "MainActivity";

	private static final String PREFS_NAMESPACE = "se.embargo.sonar";
	private static final String PREF_IMAGECOUNT = "imagecount";
	private static final String DIRECTORY = "Sonar";
	private static final String FILENAME_PATTERN = "IMGS%04d";

	/**
	 * Application wide preferences
	 */
	protected SharedPreferences _prefs;
	
	private SonogramSurface _sonogramSurface;
	private SonogramView _sonogramView;
	private HistogramView _histogramView;
	private HistogramView _histogramView2;
	private ISonar _sonar;
	
	/**
	 * Picture or video mode.
	 */
	private enum RecordState { Picture, Video, Recording }
	
	/**
	 * Current camera state. 
	 */
	private IObservableValue<RecordState> _cameraState = new WritableValue<RecordState>(RecordState.Video);
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		_prefs = getSharedPreferences(PREFS_NAMESPACE, MODE_PRIVATE);
		
		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		setContentView(R.layout.main_activity);
		_sonogramSurface = (SonogramSurface)findViewById(R.id.sonogram_surface);
		_sonogramView = (SonogramView)findViewById(R.id.sonogram);
		_histogramView = (HistogramView)findViewById(R.id.histogram);
		_histogramView2 = (HistogramView)findViewById(R.id.histogram2);

		_sonar = null;
		if (true /*Intent.ACTION_VIEW.equals(getIntent().getAction())*/) {
			Uri url = Uri.parse("file:///storage/emulated/0/Pictures/Sonar/IMGS0007.sonar");//getIntent().getData();
			if (url != null) {
				try {
					Log.i(TAG, "Opening sonar dump: " + url);
					_sonar = new StreamReader(new FileInputStream(url.getPath()));
				}
				catch (FileNotFoundException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}

		if (_sonar == null) {
			//_sonar = new Sonar(this);
		}
		
		if (_sonogramSurface != null) {
			_sonar.setController(_sonogramSurface);
			_sonar.setFilter(new CompositeFilter(_sonogramSurface, new FramerateCounter()));
			
			// Scale the surface to avoid rendering the full resolution
			DisplayMetrics dm = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(dm);
			
			float scale = 0.25f;
			int width = dm.widthPixels, height = (int) dm.heightPixels;
			int scaledwidth = (int)(width * scale);
			int scaledheight = (int)(height * scale);

			if (scaledwidth != width || scaledheight != height) {
				_sonogramSurface.getHolder().setFixedSize(scaledwidth, scaledheight);		
			}
		}
		else if (_sonogramView != null) {
			_sonar.setController(_sonogramView);
			_sonar.setFilter(new CompositeFilter(new SonogramFilter(Sonar.OPERATOR)/*, new AverageFilter()*/, _sonogramView, new FramerateCounter()));
		}
		else if (_histogramView2 != null) {
			_sonar.setController(new CompositeSonarController(_histogramView, _histogramView2));
			_sonar.setFilter(new CompositeFilter(
				new CompositeFilter(new MatchedFilter(Sonar.OPERATOR, 2, 0), new AverageFilter(), _histogramView),
				new CompositeFilter(new MatchedFilter(Sonar.OPERATOR, 2, 1), new AverageFilter(), _histogramView2), 
				new FramerateCounter()));
		}
		else if (_histogramView != null) {
			_sonar.setController(_histogramView);
			_sonar.setFilter(new CompositeFilter(new MatchedFilter(Sonar.OPERATOR), new AverageFilter(), _histogramView, new FramerateCounter()));
			//_histogramView.setZoom(3);
		}
		
		// Connect the recording mode button
		{
			final ImageButton cameraModeButton = (ImageButton)findViewById(R.id.cameraModeButton);
			cameraModeButton.setOnClickListener(new CameraModeButtonListener());
			_cameraState.addChangeListener(new RecordStateListener());
			_cameraState.setValue(RecordState.Picture);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		_sonar.start();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		_sonar.stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private class CameraModeButtonListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			_cameraState.setValue(_cameraState.getValue() != RecordState.Picture ? RecordState.Picture : RecordState.Video);
		}
	}
	
	private class RecordStateListener implements IChangeListener<RecordState> {
		private View.OnTouchListener _captureListener;

		@Override
		public void handleChange(ChangeEvent<RecordState> event) {
			final ImageButton takePhotoButton = (ImageButton)findViewById(R.id.takePhotoButton);
			final ImageButton cameraModeButton = (ImageButton)findViewById(R.id.cameraModeButton);
			//final View videoProgressLayout = findViewById(R.id.videoProgressLayout);

			switch (event.getValue()) {
				case Picture: {
					// Abort any ongoing video capture
					//_videoRecorder.abort();
					
					// Prepare to capture still images
					//_captureListener = new TakePhotoListener();
					takePhotoButton.setImageResource(R.drawable.ic_button_camera);
					//takePhotoButton.setOnTouchListener(_captureListener);
					cameraModeButton.setImageResource(R.drawable.ic_button_video);
					//videoProgressLayout.setVisibility(View.GONE);
					break;
				}
				
				case Video: {
					// Abort any ongoing video capture							
					//_videoRecorder.abort();
					
					// Prepare to record video
					_captureListener = new RecordButtonListener();
					takePhotoButton.setImageResource(R.drawable.ic_button_video);
					takePhotoButton.setOnTouchListener(_captureListener);
					cameraModeButton.setImageResource(R.drawable.ic_button_camera);
					//videoProgressLayout.setVisibility(View.VISIBLE);
					break;
				}
				
				case Recording: {
					// Recording video
					takePhotoButton.setImageResource(R.drawable.ic_button_playback_stop);
					cameraModeButton.setImageResource(R.drawable.ic_button_camera);
					//videoProgressLayout.setVisibility(View.VISIBLE);
				}
			}
		}
	}
	
	private class RecordButtonListener implements View.OnTouchListener {
		private static final long PRESS_DELAY = 350;
		private long _prevEvent = 0;
		private ISignalFilter _prevFilter;
		private StreamWriter _outputFilter;
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			//_detailedPreferences.setVisibility(View.GONE);
			
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_POINTER_DOWN: {
					// Start recording if we're not currently doing so
					if (_cameraState.getValue() != RecordState.Recording) {
						startRecording();
						_prevEvent = System.currentTimeMillis();
					}
					else {
						// Stop recording when button is released from long press
						stopRecording();
					}
					
					return true;
				}

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
					// Don't stop if the record button was just tapped quickly
					if ((System.currentTimeMillis() - _prevEvent) > PRESS_DELAY) {
						stopRecording();
					}
					
					return true;
				
				case MotionEvent.ACTION_CANCEL:
					//_videoRecorder.abort();
					return true;
			}
			
			return false;
		}
		
		private void startRecording() {
			OutputStream os;
			try {
				os = new FileOutputStream(createOutputFile(null, "sonar"));
			}
			catch (FileNotFoundException e) {
				Log.e(TAG, e.getMessage(), e);
				return;
			}
			
			_prevFilter = _sonar.getFilter();
			_outputFilter = new StreamWriter(os);
			_sonar.setFilter(new CompositeFilter(_outputFilter, _prevFilter));
			_cameraState.setValue(RecordState.Recording);
		}
		
		private void stopRecording() {
			if (_outputFilter != null) {
				_outputFilter.close();
				_outputFilter = null;
				_sonar.setFilter(_prevFilter);
			}
			
			_cameraState.setValue(RecordState.Video);
		}
	}
	
	/**
	 * @return	The directory where images are stored
	 */
	public File getStorageDirectory() {
		File result = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + DIRECTORY);
		result.mkdirs();
		return result;
	}
	
	private File createOutputFile(String inputname, String fileext) {
		String filename;
		File file;
		
		do {
			if (inputname != null) {
				// Use the original image name
				filename = new File(inputname).getName();
				filename = filename.split("\\.", 2)[0];
				filename += "." + fileext;
			}
			else {
				// Create a new sequential name
				int count = _prefs.getInt(PREF_IMAGECOUNT, 0);
				filename = String.format(FILENAME_PATTERN + "." + fileext, count);
				
				// Increment the image count
				SharedPreferences.Editor editor = _prefs.edit();
				editor.putInt(PREF_IMAGECOUNT, count + 1);
				editor.commit();
			}
			
			file = new File(getStorageDirectory(), filename);
			inputname = null;
		} while (file.exists());
		
		return file;
	}
}
