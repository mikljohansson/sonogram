package se.embargo.sonar;

import se.embargo.sonar.shader.SonogramSurface;
import se.embargo.sonar.widget.HistogramView;
import se.embargo.sonar.widget.SonogramView;
import android.content.pm.ActivityInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockFragmentActivity {
	private static final String TAG = "MainActivity";
	
	private SonogramSurface _sonogramSurface;
	private SonogramView _sonogramView;
	private HistogramView _histogramView;
	private Sonar _sonar;
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		// Switch to full screen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		setContentView(R.layout.main_activity);
		_sonogramSurface = (SonogramSurface)findViewById(R.id.sonogram);
		//_sonogramView = (SonogramView)findViewById(R.id.sonogram);
		//_histogramView = (HistogramView)findViewById(R.id.histogram);
		
		_sonar = new Sonar(this, true);
		_sonar.setController(_sonogramSurface);
		//_sonar.setController(_sonogramView);
		//_sonar.setController(_histogramView);
		
		//_histogramView.setZoom(3);
		
		// Scale the surface to avoid rendering the full resolution
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		
		float scale = 0.2f;
		int width = dm.widthPixels, height = (int) dm.heightPixels;
		int scaledwidth = (int)(width * scale);
		int scaledheight = (int)(height * scale);

		if (scaledwidth != width || scaledheight != height) {
			_sonogramSurface.getHolder().setFixedSize(scaledwidth, scaledheight);		
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
}
