package it.l_soft.wows.indicators.bands;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Bollinger Bands.
 *
 * - Middle = SMA(period)
 * - Upper  = SMA + k * StdDev
 * - Lower  = SMA - k * StdDev
 *
 * Uses a fixed window with a ring buffer and maintains running sum and sum of squares.
 * Returns Double.NaN (NOT_DEFINED) until 'period' bars are processed.
 *
 * Note: 'k' is an integer multiplier here (commonly 2). If you need fractional k (e.g., 2.5),
 * extend your regex/factory to accept doubles and add a (int, double, Price) ctor.
 */
public final class Bollinger extends AbstractIndicator {

	private final int period;
    private final int k; // band width multiplier (commonly 2)
    private final ToDoubleFunction<Bar> input;

    // Ring buffer for last 'period' values
    private final double[] window;
    private int idx = 0;
    private int count = 0;

    // Running aggregates
    private double sum = 0.0;
    private double sumSq = 0.0;

    // Outputs
    private double mid = NOT_DEFINED;  // SMA
    private double up  = NOT_DEFINED;  // Upper band
    private double low = NOT_DEFINED;  // Lower band

    /** Standard Bollinger on chosen price (e.g., CLOSE), integer k (e.g., 2). */
    public Bollinger(int period, int k, Price price) {
        this(period, k, price.extractor);
    }

    /** Bollinger fed by a custom extractor. */
    public Bollinger(int period, int k, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        if (k < 0) throw new IllegalArgumentException("k must be >= 0");
        this.period = period;
        this.k = k;
        this.input = input;
        this.window = new double[period];
    }

    /** Convenience: default k=2, price=CLOSE. */
    public Bollinger(int period) {
        this(period, 2, Price.CLOSE);
    }

    @Override
    public double add(Bar bar) {
        final double x = input.applyAsDouble(bar);

        if (count < period) {
            // Warm-up: fill window
            window[idx++] = x;
            if (idx == period) idx = 0;
            sum   += x;
            sumSq += x * x;
            count++;

            if (count < period) {
                mid = up = low = NOT_DEFINED;
                return mid;
            }
            // First full window—compute bands
            computeBands();
            return mid;
        }

        // Steady state: remove oldest, add newest
        final double oldest = window[idx];
        window[idx] = x;
        idx++; if (idx == period) idx = 0;

        sum   += x - oldest;
        sumSq += x * x - oldest * oldest;

        computeBands();
        return mid;
    }

    private void computeBands() {
        mid = sum / period;
        double meanSq = sumSq / period;
        double variance = meanSq - mid * mid;   // population variance over the window
        if (variance < 0) variance = 0;         // guard tiny negatives from FP error
        double std = Math.sqrt(variance);

        up  = mid + k * std;
        low = mid - k * std;
    }

    @Override public double value() { return mid; }        // middle band
    public double upper() { return up; }
    public double lower() { return low; }

    /** Bandwidth = (Upper - Lower) / Middle. Returns NaN if middle is 0 or undefined. */
    public double bandwidth() {
        if (Double.isNaN(up) || Double.isNaN(low) || Double.isNaN(mid) || mid == 0.0) return NOT_DEFINED;
        return (up - low) / mid;
    }

    /** %B = (Price - Lower) / (Upper - Lower) * 100. Uses the latest input price. */
    public double percentB(double lastPrice) {
        if (Double.isNaN(up) || Double.isNaN(low)) return NOT_DEFINED;
        double range = up - low;
        if (range == 0.0) return 50.0; // flat band → neutral
        return 100.0 * (lastPrice - low) / range;
    }

    @Override
    public void reset() {
        idx = 0; count = 0;
        sum = sumSq = 0.0;
        mid = up = low = NOT_DEFINED;
        for (int i = 0; i < period; i++) window[i] = 0.0;
    }

    public boolean isReady() { return count >= period; }
}
