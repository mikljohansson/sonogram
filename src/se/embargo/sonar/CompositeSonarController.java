package se.embargo.sonar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.Rect;

public class CompositeSonarController implements ISonarController {
	private List<ISonarController> _children = new ArrayList<ISonarController>();

	public CompositeSonarController(ISonarController... filters) {
		_children.addAll(Arrays.asList(filters));
	}
	
	@Override
	public void setSonarResolution(Rect resolution) {
		for (ISonarController controller : _children) {
			controller.setSonarResolution(resolution);
		}
	}

	@Override
	public Rect getSonarWindow() {
		for (ISonarController controller : _children) {
			return controller.getSonarWindow();
		}

		return null;
	}

	@Override
	public Rect getSonarCanvas() {
		for (ISonarController controller : _children) {
			return controller.getSonarCanvas();
		}

		return null;
	}
}
