package it.l_soft.wows.ga;

import it.l_soft.wows.indicators.IndicatorContext;

public final class Gene implements GeneInterface {
    private static final double SIGNAL_MAX_ABS = 50.0;

    private final String name;
    private final int[] indicatorIndices;
    private final double[] weights;
    private double score;

    public Gene(String name, int[] indicatorIndices, double[] weights) {
        this.name = name;
        this.indicatorIndices = indicatorIndices;
        this.weights = weights;
    }

    /** Raw gene signal as weighted sum of normalized indicators, clamped to [-50, 50]. */
    @Override
    public double computeSignal(int t, IndicatorContext ctx) {
        double sum = 0.0;
        for (int i = 0; i < indicatorIndices.length; i++) {
            double x = ctx.getNormalizedIndicator(indicatorIndices[i], t);
            sum += weights[i] * x;
        }
        return clamp50(sum);
    }

    /**
     * Prediction of normalized market move, on the SAME SCALE as marketMoveNorm: [-1, 1].
     * This is what you compare against marketMoveNorm.
     */
    public double computePredictedMoveNorm(int t, IndicatorContext ctx) {
        double raw = computeSignal(t, ctx);       // [-50, 50]
        double scaled = raw / SIGNAL_MAX_ABS;     // [-1, 1] if raw fully spans ±50
        return clamp1(scaled);
    }

    private static double clamp50(double x) {
        if (x >  SIGNAL_MAX_ABS) return  SIGNAL_MAX_ABS;
        if (x < -SIGNAL_MAX_ABS) return -SIGNAL_MAX_ABS;
        return x;
    }

    private static double clamp1(double x) {
        if (x >  1.0) return  1.0;
        if (x < -1.0) return -1.0;
        return x;
    }

    public String getName() {
        return name;
    }

    public int[] getIndicatorIndices() {
        return indicatorIndices;
    }

    public double[] getWeights() {
        return weights;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
