package se.embargo.sonar;

public interface ISonarListener {
	void receive(int offset, float[] output);
}
