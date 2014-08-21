package se.embargo.sonar;

import se.embargo.sonar.dsp.AverageFilter;
import se.embargo.sonar.dsp.CompositeFilter;
import se.embargo.sonar.dsp.MatchedFilter;
import se.embargo.sonar.dsp.SonogramFilter;
import se.embargo.sonar.io.ISonar;
import se.embargo.sonar.io.Sonar;
import se.embargo.sonar.shader.SonogramSurface;
import se.embargo.sonar.widget.HistogramView;
import se.embargo.sonar.widget.SonogramView;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockFragmentActivity {
	private SonogramSurface _sonogramSurface;
	private SonogramView _sonogramView;
	private HistogramView _histogramView;
	private HistogramView _histogramView2;
	private ISonar _sonar;
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

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
		
		if (_sonogramSurface != null) {
			_sonar = new Sonar(this, true);
			_sonar.setController(_sonogramSurface);
			_sonar.setFilter(_sonogramSurface);
			
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
			_sonar = new Sonar(this, true);
			_sonar.setController(_sonogramView);
			_sonar.setFilter(new CompositeFilter(new SonogramFilter(Sonar.OPERATOR)/*, new AverageFilter()*/, _sonogramView));
		}
		else if (_histogramView2 != null) {
			_sonar = new Sonar(this, true);
			_sonar.setController(new CompositeSonarController(_histogramView, _histogramView2));
			_sonar.setFilter(new CompositeFilter(
				new CompositeFilter(new MatchedFilter(Sonar.OPERATOR, 2, 0), new AverageFilter(), _histogramView),
				new CompositeFilter(new MatchedFilter(Sonar.OPERATOR, 2, 1), new AverageFilter(), _histogramView2)));
		}
		else if (_histogramView != null) {
			_sonar = new Sonar(this, false);
			_sonar.setController(_histogramView);
			_sonar.setFilter(new CompositeFilter(new MatchedFilter(Sonar.OPERATOR), new AverageFilter(), _histogramView));
			//_histogramView.setZoom(3);
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
