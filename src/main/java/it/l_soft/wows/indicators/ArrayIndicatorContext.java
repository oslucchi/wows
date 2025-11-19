package it.l_soft.wows.indicators;

public final class ArrayIndicatorContext implements IndicatorContext {

    private final int length;
    private final double[] closes;
    private final double[] atrValues;
    private final double[][] normIndicators; // [indicatorIndex][t]

    public ArrayIndicatorContext(int length,
                                 int numIndicators) {
        this.length = length;
        this.closes = new double[length];
        this.atrValues = new double[length];
        this.normIndicators = new double[numIndicators][length];
    }

    // "Setters" used only when building the context (one-time)
    public void setClose(int t, double close) {
        closes[t] = close;
    }

    public void setAtr(int t, double atr) {
        atrValues[t] = atr;
    }

    public void setNormalizedIndicator(int indicatorIndex, int t, double value) {
        normIndicators[indicatorIndex][t] = value;
    }

    // === Interface implementation ===

    @Override
    public int length() {
        return length;
    }

    @Override
    public double getClose(int t) {
        return closes[t];
    }

    @Override
    public double getAtr(int t) {
        return atrValues[t];
    }

    @Override
    public double getNormalizedIndicator(int indicatorIndex, int t) {
        return normIndicators[indicatorIndex][t];
    }

	@Override
	public double getCloseAt(int tPlusH) {
		// TODO Auto-generated method stub
		return 0;
	}
}
