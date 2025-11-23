package it.l_soft.wows.indicators.momentum;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;
import it.l_soft.wows.indicators.trend.SMA;

/**
 * Streaming Detrended Price Oscillator (DPO).
 *
 * Standard formulation:
 *   shift = period / 2 + 1
 *   DPO_t = price_{t-shift} - SMA(price, period)_t
 *
 * - Uses a streaming SMA for the mean.
 * - Keeps a ring buffer of recent prices to access price_{t-shift}.
 * - Returns NOT_DEFINED until both SMA is ready and we have >= shift+1 bars.
 */
public final class DPO extends AbstractIndicator {

    private final int period;
    private final int shift;
    private final ToDoubleFunction<Bar> input;
    private final SMA sma;

    private final double[] ring;  // last (shift+1) prices
    private int ringIdx = 0;
    private int barCount = 0;

    private double dpo = NOT_DEFINED;

    /** DPO on a chosen price (e.g., Price.CLOSE). */
    public DPO(int period, Price price) {
        this(period, price.extractor);
    }

    /** DPO with custom input extractor. */
    public DPO(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.shift  = period / 2 + 1;
        this.input  = input;
        this.sma    = new SMA(period, input);
        this.ring   = new double[shift + 1];
    }

    @Override
    public double add(Bar bar) {
        final double p = input.applyAsDouble(bar);

        // Store latest price in ring
        ring[ringIdx++] = p;
        if (ringIdx == ring.length) ringIdx = 0;
        barCount++;

        // Update SMA
        double mean = sma.add(bar);
        if (Double.isNaN(mean)) {
            dpo = NOT_DEFINED;
            return dpo;
        }

        // Need at least shift+1 bars to access price_{t-shift}
        if (barCount <= shift) {
            dpo = NOT_DEFINED;
            return dpo;
        }

        // ringIdx points to next write position => last written is at ringIdx-1
        int pos = ringIdx - 1 - shift;
        if (pos < 0) pos += ring.length;

        final double priceShifted = ring[pos];
        dpo = priceShifted - mean;
        return dpo;
    }

    @Override
    public double value() { return dpo; }

    @Override
    public void reset() {
        sma.reset();
        ringIdx = 0;
        barCount = 0;
        dpo = NOT_DEFINED;
        for (int i = 0; i < ring.length; i++) ring[i] = 0.0;
    }

    public boolean isReady() {
        return !Double.isNaN(dpo);
    }
}
