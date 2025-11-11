package it.l_soft.wows.indicators.trend;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;

/**
 * Streaming Simple Moving Average (SMA).
 *
 * - Keeps a ring buffer of the last 'period' inputs and a running sum.
 * - Until 'period' samples are collected, returns Double.NaN (NOT_DEFINED).
 * - O(1) per update; O(period) memory for the ring.
 */
public final class SMA implements Indicator {
    private int period = 0;
    private ToDoubleFunction<Bar> input = null;

    private final double[] window; // ring buffer
    private int idx = 0;           // next position to overwrite
    private int count = 0;         // how many samples seen (<= period)
    private double sum = 0.0;      // running sum of current window
    private double sma = NOT_DEFINED;

    /** Standard SMA on chosen price (e.g., Price.CLOSE). */
    public SMA(int period, Price price) {
        this(period, price.extractor);
    }

    /** SMA fed by a custom extractor from Bar to double. */
    public SMA(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.input = input;
        this.window = new double[period];
    }

    @Override
    public double add(Bar bar) {
        final double x = input.applyAsDouble(bar);

        if (count < period) {
            // Warming up: fill window
            window[idx++] = x;
            sum += x;
            count++;
            if (idx == period) idx = 0;

            sma = (count == period) ? (sum / period) : NOT_DEFINED;
            return sma;
        }

        // Steady state: subtract oldest, add newest
        final double oldest = window[idx];
        window[idx] = x;
        idx++; if (idx == period) idx = 0;

        sum += x - oldest;
        sma = sum / period;
        return sma;
    }

    @Override public double value() { return sma; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        sum = 0.0;
        sma = NOT_DEFINED;
        // clear ring
        for (int i = 0; i < period; i++) window[i] = 0.0;
    }

    public boolean isReady() { return count >= period; }
}
