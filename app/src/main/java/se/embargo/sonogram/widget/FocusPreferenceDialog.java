package se.embargo.sonogram.widget;

import se.embargo.core.databinding.DataBindingContext;
import se.embargo.core.databinding.WidgetProperties;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.widget.SeekBarDialog;
import se.embargo.sonogram.R;
import android.app.Activity;
import android.view.View;
import android.widget.SeekBar;
import android.widget.ToggleButton;

public class FocusPreferenceDialog extends SeekBarDialog {
	private DataBindingContext _context = new DataBindingContext();
	private IObservableValue<Float> _baseline;
	private IObservableValue<Boolean> _autofocus;
	private IObservableValue<Float> _autofocusvalue;
	
	public FocusPreferenceDialog(Activity context, IObservableValue<Float> baseline, IObservableValue<Boolean> autofocus, IObservableValue<Float> autofocusvalue) {
		super(context, baseline, 0.001f, -0.25f, 0.25f);
		setLayoutResource(R.layout.focus_preference_dialog);
		setFormat("%.03fm");
		_baseline = baseline;
		_autofocus = autofocus;
		_autofocusvalue = autofocusvalue;
	}
	
	@Override
	protected void build(View parent) {
		super.build(parent);
		
		final ToggleButton autofocusbutton = (ToggleButton)parent.findViewById(R.id.autoFocusButton);
		_context.bindValue(WidgetProperties.checked().observe(autofocusbutton), _autofocus);
		
		autofocusbutton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (autofocusbutton.isChecked()) {
					_baseline.setValue(_autofocusvalue.getValue());
				}
			}
		});
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		super.onProgressChanged(seekBar, progress, fromUser);
		
		if (fromUser) {
			_autofocus.setValue(false);
		}
	}
}
