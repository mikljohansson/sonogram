package se.embargo.sonar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import se.embargo.core.databinding.PreferenceProperties;
import se.embargo.core.databinding.observable.ChangeEvent;
import se.embargo.core.databinding.observable.IChangeListener;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.core.widget.ListPreferenceDialog;
import se.embargo.core.widget.SeekBarDialog;
import se.embargo.sonar.dsp.CompositeFilter;
import se.embargo.sonar.dsp.FramerateCounter;
import se.embargo.sonar.dsp.ISignalFilter;
import se.embargo.sonar.dsp.MatchedFilter;
import se.embargo.sonar.dsp.SonogramFilter;
import se.embargo.sonar.io.ISonar;
import se.embargo.sonar.io.Sonar;
import se.embargo.sonar.io.StreamReader;
import se.embargo.sonar.io.StreamWriter;
import se.embargo.sonar.widget.HistogramView;
import se.embargo.sonar.widget.SonogramView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockFragmentActivity {
	private static final String TAG = "MainActivity";

	private static final String PREFS_NAMESPACE = "se.embargo.sonar";
	private static final String PREF_IMAGECOUNT = "imagecount";
	private static final String PREF_BASELINE = "baseline";
	private static final float PREF_BASELINE_DEFAULT = 0.12f;
	private static final String PREF_VISUALIZATION = "visualization";
	
	private static final String DIRECTORY = "Sonar";
	private static final String FILENAME_PATTERN = "IMGS%04d";

	/**
	 * Application wide preferences
	 */
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();
	
	private View _sonogramLayout, _histogramLayout, _dualHistogramLayout;
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
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		_prefs = getSharedPreferences(PREFS_NAMESPACE, MODE_PRIVATE);
		_baseline = PreferenceProperties.floating(PREF_BASELINE, PREF_BASELINE_DEFAULT).observe(_prefs);
		
		// Keep screen on while this activity is focused 
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		setContentView(R.layout.main_activity);
		_sonogramLayout = findViewById(R.id.sonogramLayout);
		_histogramLayout = findViewById(R.id.histogramLayout);
		_dualHistogramLayout = findViewById(R.id.dualHistogramLayout);

		_sonar = null;
		if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
			Uri url = getIntent().getData();
			//Uri url = Uri.parse("file:///storage/emulated/0/Pictures/Sonar/IMGS0009.sonar");
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
			_sonar = new Sonar(this);
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
	
	private class FocusButtonListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			SeekBarDialog dialog = new SeekBarDialog(MainActivity.this, _baseline, 0.001f, -0.25f, 0.25f);
			dialog.setFormat("%.03f");
			dialog.show();
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

		@Override
		public void onClick(View v) {
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
					Toast.makeText(MainActivity.this, R.string.saved_sonar_recording, Toast.LENGTH_SHORT).show();
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
				_sonogramLayout.setVisibility(View.GONE);
				_histogramLayout.setVisibility(View.GONE);
				_dualHistogramLayout.setVisibility(View.GONE);
				
				if ("histogram".equals(value)) {
					HistogramView histogram = (HistogramView)_histogramLayout.findViewById(R.id.histogram);
					_sonar.init(histogram, new CompositeFilter(new MatchedFilter(), new MatchedFilter(1).reduce(true), /*new AverageFilter(), */histogram, new FramerateCounter()));
					_histogramLayout.setVisibility(View.VISIBLE);
				}
				else if ("dual_histogram".equals(value)) {
					HistogramView histogram = (HistogramView)_dualHistogramLayout.findViewById(R.id.histogram);
					HistogramView histogram2 = (HistogramView)_dualHistogramLayout.findViewById(R.id.histogram2);
					
					_sonar.init(
						new CompositeSonarController(histogram, histogram2),
						new CompositeFilter(
							new CompositeFilter(new MatchedFilter(0), /*new AverageFilter(), */histogram),
							new CompositeFilter(new MatchedFilter(1), /*new MatchedFilter(1).reduce(true),*/ /*new AverageFilter(), */histogram2), 
							new FramerateCounter()));

					_dualHistogramLayout.setVisibility(View.VISIBLE);
				}
				else {
					SonogramView sonogram = (SonogramView)_sonogramLayout.findViewById(R.id.sonogram);
					_sonar.init(sonogram, new CompositeFilter(new SonogramFilter(_baseline)/*, new AverageFilter()*/, sonogram, new FramerateCounter()));
					_sonogramLayout.setVisibility(View.VISIBLE);
				}

				/*
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
				*/
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
