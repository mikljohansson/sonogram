package se.embargo.sonar.io;

import se.embargo.sonar.dsp.ISignalFilter;

public interface ISonar {
	public abstract void init(ISonarController controller, ISignalFilter filterS);

	public abstract ISonarController getController();
	
	public abstract ISignalFilter getFilter();
	
	public abstract void start();

	public abstract void stop();
}