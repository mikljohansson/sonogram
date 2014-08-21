package se.embargo.sonar.io;

import se.embargo.sonar.dsp.ISignalFilter;

public interface ISonar {

	public abstract void setController(ISonarController controller);

	public abstract void setFilter(ISignalFilter filter);

	public abstract void start();

	public abstract void stop();

}