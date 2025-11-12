package it.l_soft.wows.indicators.momentum;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Rate of Change (ROC), percent version:
 *   ROC_t = ((P_t - P_{t-n}) / P_{t-n}) * 100
 *
 * Notes:
 * - Warmup: needs 'period' prior samples to compute the first ROC (returns NaN before that).
 * - Uses a ring buffer to access P_{t-n} in O(1).
 * - If P_{t-n} == 0, returns NOT_DEFINED to avoid division by zero.
 */
public final class ROC extends AbstractIndicator {
	
    private int period;
    private ToDoubleFunction<Bar> input;

    private final double[] ring; // last 'period' inputs
    private int idx = 0;         // next slot to overwrite
    private int count = 0;       // samples seen (<= period)
    private double roc = NOT_DEFINED;

    /** Standard ROC on chosen price (e.g., Price.CLOSE). */
    public ROC(int period, Price price) {
        this(period, price.extractor);
    }

    /** ROC fed by a custom extractor from Bar to double. */
    public ROC(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.input = input;
        this.ring = new double[period];
    }

    @Override
    public double add(Bar bar) {
        final double x = input.applyAsDouble(bar);

        if (count < period) {
            // Fill until we have 'period' samples to compare against
            ring[idx++] = x;
            if (idx == period) idx = 0;
            count++;
            roc = NOT_DEFINED;
            return roc;
        }

        // Oldest value is at current idx (slot to be overwritten)
        final double xN = ring[idx];
        ring[idx] = x;                    // overwrite with newest
        idx++; if (idx == period) idx = 0;

        if (xN == 0.0) {
            roc = NOT_DEFINED;            // avoid div-by-zero
        } else {
            roc = ((x - xN) / xN) * 100.0;
        }
        return roc;
    }

    @Override public double value() { return roc; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        roc = NOT_DEFINED;
        for (int i = 0; i < period; i++) ring[i] = 0.0;
    }

    public boolean isReady() { return count >= period; }
}
