package se.embargo.sonogram;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import se.embargo.core.databinding.PreferenceProperties;
import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.core.io.Files;
import se.embargo.core.widget.ListPreferenceDialog;
import se.embargo.sonogram.dsp.CompositeFilter;
import se.embargo.sonogram.dsp.CrossCorrelationFilter;
import se.embargo.sonogram.dsp.FramerateCounter;
import se.embargo.sonogram.dsp.ISignalFilter;
import se.embargo.sonogram.dsp.MeanPeakDetector;
import se.embargo.sonogram.dsp.MonoFilter;
import se.embargo.sonogram.dsp.SmoothenFilter;
import se.embargo.sonogram.io.ISonar;
import se.embargo.sonogram.io.Sonar;
import se.embargo.sonogram.io.StreamReader;
import se.embargo.sonogram.io.StreamWriter;
import se.embargo.sonogram.shader.SonogramSurface;
import se.embargo.sonogram.widget.FocusPreferenceDialog;

import android.app.Activity;
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
import android.widget.Toast;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";

	private static final String PREFS_NAMESPACE = "se.embargo.sonogram";
	private static final String PREF_IMAGECOUNT = "imagecount";
	
	private static final String PREF_BASELINE = "baseline";
	private static final float PREF_BASELINE_DEFAULT = 0.12f;
	
	private static final String PREF_AUTOFOCUS = "autofocus";
	private static final String PREF_AUTOFOCUS_VALUE = "autofocusvalue";
	private static final boolean PREF_AUTOFOCUS_DEFAULT = true;
	
	private static final String PREF_VISUALIZATION = "visualization";
	
	private static final String DIRECTORY = "Sonogram";
	private static final String FILENAME_PATTERN = "IMGS%04d";

	/**
	 * Application wide preferences
	 */
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private SonogramSurface _sonogram;
	private ISonar _sonar;
	
	/**
	 * Picture or video mode.
	 */
	private enum RecordState { Picture, Video, Recording }
	
	/**
	 * Current camera state. 
	 */
	private IObservableValue<RecordState> _cameraState = new WritableValue<RecordState>(RecordState.Picture);
	private IObservableValue<Float> _baseline;
	private IObservableValue<Boolean> _autofocus;
	private IObservableValue<Float> _autofocusvalue;
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		_prefs = getSharedPreferences(PREFS_NAMESPACE, MODE_PRIVATE);
		_baseline = PreferenceProperties.floating(PREF_BASELINE, PREF_BASELINE_DEFAULT).observe(_prefs);
		_autofocus = PreferenceProperties.bool(PREF_AUTOFOCUS, PREF_AUTOFOCUS_DEFAULT).observe(_prefs);
		
		_autofocusvalue = PreferenceProperties.floating(PREF_AUTOFOCUS_VALUE, PREF_BASELINE_DEFAULT).observe(_prefs);
		_autofocusvalue.addChangeListener(new IChangeListener<Float>() {
			@Override
			public void handleChange(final ChangeEvent<Float> event) {
				if (_autofocus.getValue()) {
					_baseline.setValue(event.getValue());
				}
			}
		});
		
		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		setContentView(R.layout.main_activity);
		_sonogram = (SonogramSurface)findViewById(R.id.sonogramSurface);

		_sonar = null;
		if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
			Uri url = getIntent().getData();
			//Uri url = Uri.parse("file:///storage/emulated/0/Pictures/Sonar/IMGS0048.sonar");
			if (url != null) {
				Log.i(TAG, "Opening sonar dump: " + url);
				_sonar = new StreamReader(url.getPath());
			}
		}

		if (_sonar == null) {
			_sonar = new Sonar(this, _autofocusvalue);
		}
		
		_prefsListener.onSharedPreferenceChanged(_prefs, PREF_VISUALIZATION);
		
		// Connect the recording mode button
		{
			final ImageButton cameraModeButton = (ImageButton)findViewById(R.id.cameraModeButton);
			cameraModeButton.setOnClickListener(new CameraModeButtonListener());
			_cameraState.addChangeListener(new RecordStateListener());
			_cameraState.setValue(_cameraState.getValue());
		}
		
		// Connect the focus button
		{
			final ImageButton focusButton = (ImageButton)findViewById(R.id.focusButton);
			focusButton.setOnClickListener(new FocusButtonListener());
		}
		
		// Connect the visualization button
		{
			final ImageButton button = (ImageButton)findViewById(R.id.visualizationButton);
			button.setOnClickListener(new ListPreferenceDialog(
				this, _prefs, PREF_VISUALIZATION, getResources().getString(R.string.pref_visualization_default),
				R.string.menu_option_visualization, R.array.pref_visualization_labels, R.array.pref_visualization_values));
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		_sonar.start();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		_sonar.stop();
		_prefs.unregisterOnSharedPreferenceChangeListener(_prefsListener);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
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
	
	private class FocusButtonListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			new FocusPreferenceDialog(MainActivity.this, _baseline, _autofocus, _autofocusvalue).show();
		}
	}

	private class RecordStateListener implements IChangeListener<RecordState> {
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
					takePhotoButton.setImageResource(R.drawable.ic_button_camera);
					takePhotoButton.setOnClickListener(new TakePhotoListener());
					cameraModeButton.setImageResource(R.drawable.ic_button_video);
					//videoProgressLayout.setVisibility(View.GONE);
					break;
				}
				
				case Video: {
					// Abort any ongoing video capture							
					//_videoRecorder.abort();
					
					// Prepare to record video
					takePhotoButton.setImageResource(R.drawable.ic_button_video);
					takePhotoButton.setOnTouchListener(new RecordButtonListener());
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
	
	private class TakePhotoListener implements View.OnClickListener, StreamWriter.IStreamListener {
		private ISignalFilter _prevFilter;
		private StreamWriter _outputFilter;
		private File _file;

		@Override
		public void onClick(View v) {
			OutputStream os;
			_file = createOutputFile(null, "sonar");
			
			try {
				os = new FileOutputStream(_file);
			}
			catch (FileNotFoundException e) {
				Log.e(TAG, e.getMessage(), e);
				return;
			}
			
			if (_outputFilter != null) {
				_outputFilter.close();
			}
			
			_prevFilter = _sonar.getFilter();
			_outputFilter = new StreamWriter(os, _baseline, 1);
			_outputFilter.setListener(this);
			_sonar.init(_sonar.getController(), new CompositeFilter(_outputFilter, _prevFilter));
		}
		
		@Override
		public void onClosed() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_sonar.init(_sonar.getController(), _prevFilter);
					_outputFilter = null;
					_prevFilter = null;
					Toast.makeText(MainActivity.this, getString(R.string.saved_sonar_recording, Files.getTrailingPath(_file.toString(), 3)), Toast.LENGTH_SHORT).show();
				}
			});
		}
	}
	
	private class RecordButtonListener implements View.OnTouchListener, StreamWriter.IStreamListener {
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
			
			if (_outputFilter != null) {
				_outputFilter.close();
			}
			
			_prevFilter = _sonar.getFilter();
			_outputFilter = new StreamWriter(os, _baseline);
			_outputFilter.setListener(this);
			_sonar.init(_sonar.getController(), new CompositeFilter(_outputFilter, _prevFilter));
			_cameraState.setValue(RecordState.Recording);
		}
		
		private void stopRecording() {
			if (_outputFilter != null) {
				_outputFilter.close();
			}
			
			_cameraState.setValue(RecordState.Video);
		}

		@Override
		public void onClosed() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_sonar.init(_sonar.getController(), _prevFilter);
					_outputFilter = null;
					_prevFilter = null;
					_cameraState.setValue(RecordState.Video);
					Toast.makeText(MainActivity.this, R.string.saved_sonar_recording, Toast.LENGTH_SHORT).show();
				}
			});
		}
	}
	
	/**
	 * Listens for preference changes and applies updates
	 */
	private class PreferencesListener implements SharedPreferences.OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (PREF_VISUALIZATION.equals(key)) {
				String value = prefs.getString(PREF_VISUALIZATION, getString(R.string.pref_visualization_default));
				CompositeFilter filter = new CompositeFilter(new CompositeFilter(
					new CrossCorrelationFilter()
					, new SmoothenFilter()
					, new MeanPeakDetector()
					/*, new AmplificationFilter()
					, new LeadingEdgeFilter()*/));
				
				if ("histogram".equals(value)) {
					_sonogram.setVisualization(SonogramSurface.Visualization.Histogram);
				}
				else if ("raw".equals(value)) {
					_sonogram.setVisualization(SonogramSurface.Visualization.Histogram);
					filter = new CompositeFilter(
						new CrossCorrelationFilter()
						, new MonoFilter()
						, new SmoothenFilter()
						, new MeanPeakDetector()
						//, new AmplificationFilter() 
						//, new LeadingEdgeFilter(0, 2)
						);
				}
				else if ("triangulate".equals(value)) {
					_sonogram.setVisualization(SonogramSurface.Visualization.Triangulate);
				}
				else {
					_sonogram.setVisualization(SonogramSurface.Visualization.Sonogram);
					filter = new CompositeFilter();
				}
				
				_sonar.init(_sonogram, new CompositeFilter(filter, _sonogram, new FramerateCounter()));
				
				// Scale the surface to avoid rendering the full resolution
				DisplayMetrics dm = new DisplayMetrics();
				getWindowManager().getDefaultDisplay().getMetrics(dm);
				
				float scale = 0.25f;
				int width = dm.widthPixels, height = (int) dm.heightPixels;
				int scaledwidth = (int)(width * scale);
				int scaledheight = (int)(height * scale);

				if (scaledwidth != width || scaledheight != height) {
					_sonogram.getHolder().setFixedSize(scaledwidth, scaledheight);		
				}
			}
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
