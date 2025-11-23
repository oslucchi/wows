package it.l_soft.wows.indicators.trend;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Weighted Moving Average (WMA).
 *
 * WMA_t = (sum_{k=1..period} k * P_{t+1-k}) / (period*(period+1)/2)
 *
 * - Uses a ring buffer of the last 'period' inputs.
 * - Returns NOT_DEFINED until 'period' samples are available.
 * - O(period) per update, O(period) memory.
 */
public final class WMA extends AbstractIndicator {

    private final int period;
    private final ToDoubleFunction<Bar> input;

    private final double[] window; // ring buffer of last 'period' values
    private int idx = 0;           // next slot to overwrite
    private int count = 0;         // samples seen (<= period)

    private double wma = NOT_DEFINED;

    /** Standard WMA on chosen price (e.g., Price.CLOSE). */
    public WMA(int period, Price price) {
        this(period, price.extractor);
    }

    /** WMA fed by a custom extractor. */
    public WMA(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.input  = input;
        this.window = new double[period];
    }

    @Override
    public double add(Bar bar) {
        final double x = input.applyAsDouble(bar);

        window[idx++] = x;
        if (idx == period) idx = 0;
        if (count < period) count++;

        if (count < period) {
            wma = NOT_DEFINED;
            return wma;
        }

        // Compute WMA with weights 1..period, higher weight to most recent
        // Oldest => weight 1, newest => weight 'period'
        double num = 0.0;
        int weight = 1;
        int pos = idx; // idx is next-to-write => oldest element
        for (int i = 0; i < period; i++) {
            if (pos == period) pos = 0;
            num += weight * window[pos];
            weight++;
            pos++;
        }
        double denom = period * (period + 1) / 2.0;
        wma = num / denom;
        return wma;
    }

    @Override
    public double value() { return wma; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        wma = NOT_DEFINED;
        for (int i = 0; i < period; i++) window[i] = 0.0;
    }

    public boolean isReady() { return count >= period; }
}
