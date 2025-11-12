package it.l_soft.wows.indicators;

public abstract class AbstractIndicator implements Indicator {
	protected double normalizedValue;
    @Override public double getNormalizedValue() { return normalizedValue; }
    @Override public void setNormalizedValue(double v) { normalizedValue = v; }
}
