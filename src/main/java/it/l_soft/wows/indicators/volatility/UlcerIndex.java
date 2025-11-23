package it.l_soft.wows.indicators.volatility;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Ulcer Index.
 *
 * For a lookback of 'period':
 *   Cmax = max(close over window)
 *   DD_i = 100 * (Cmax - close_i) / Cmax
 *   UI   = sqrt( (1/period) * sum(DD_i^2) )
 *
 * - Uses a ring buffer of closes.
 * - Returns NOT_DEFINED until 'period' closes are available.
 */
public final class UlcerIndex extends AbstractIndicator {

    private final int period;
    private final double[] closes;

    private int idx   = 0;
    private int count = 0;

    private double ui = NOT_DEFINED;

    public UlcerIndex(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.closes = new double[period];
    }

    @Override
    public double add(Bar bar) {
        double close = bar.getClose();

        closes[idx] = close;
        idx++;
        if (idx == period) idx = 0;
        if (count < period) count++;

        if (count < period) {
            ui = NOT_DEFINED;
            return ui;
        }

        // compute Cmax and UI over the window
        double cmax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < period; i++) {
            if (closes[i] > cmax) cmax = closes[i];
        }

        if (cmax <= 0.0 || !Double.isFinite(cmax)) {
            ui = 0.0;
            return ui;
        }

        double sumSq = 0.0;
        for (int i = 0; i < period; i++) {
            double dd = 100.0 * (cmax - closes[i]) / cmax;
            sumSq += dd * dd;
        }

        ui = Math.sqrt(sumSq / period);
        return ui;
    }

    @Override
    public double value() { return ui; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        ui = NOT_DEFINED;
        for (int i = 0; i < period; i++) closes[i] = 0.0;
    }

    public boolean isReady() { return count >= period; }
}
