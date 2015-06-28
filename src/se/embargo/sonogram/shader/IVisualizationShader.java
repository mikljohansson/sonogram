package se.embargo.sonogram.shader;

public interface IVisualizationShader {
	public abstract void draw(float[] operator, float[] samples0, float[] samples1);
}
