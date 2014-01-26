package se.embargo.sonar;

import se.embargo.sonar.widget.HistogramView;
import se.embargo.sonar.widget.SonogramView;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockFragmentActivity {
	private static final String TAG = "MainActivity";
	
	private SonogramView _sonogramView;
	private HistogramView _histogramView;
	private Sonar _sonar;
	
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.main_activity);
		_sonogramView = (SonogramView)findViewById(R.id.sonogram);
		//_histogramView = (HistogramView)findViewById(R.id.histogram);
		
		_sonar = new Sonar(this, true);
		_sonar.setController(_sonogramView);
		//_sonar.setController(_histogramView);
		
		//_histogramView.setZoom(3);
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
