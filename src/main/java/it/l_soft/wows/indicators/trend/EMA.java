package it.l_soft.wows.indicators.trend;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;

/** Streaming EMA: O(1) memory, single pass, no archives. */
public final class EMA implements Indicator {

    public static final double NOT_DEFINED = Double.NaN;

    private final int period;
    private final double alpha;
    private final ToDoubleFunction<Bar> input;

    private int count = 0;       // bars seen
    private double warmupSum = 0;// only used during warmup for initial SMA seed
    private boolean seeded = false;
    private double ema = NOT_DEFINED;

    /** Standard EMA on chosen Price (e.g., Price.CLOSE). */
    public EMA(int period, Price price) {
        this(period, price.extractor);
    }

    /** EMA fed by a custom extractor from Bar to double. */
    public EMA(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.alpha = 2.0 / (period + 1.0);
        this.input = input;
    }

    /** Feed one bar; returns latest EMA or NOT_DEFINED until period is reached. */
    public double add(Bar bar) {
        final double x = input.applyAsDouble(bar);

        if (!seeded) {
            // Warmup: accumulate first `period` values to seed EMA with SMA
            warmupSum += x;
            count++;
            if (count < period) {
                return NOT_DEFINED; // not enough data yet
            }
            // count == period: seed EMA with SMA of first `period`
            ema = warmupSum / period;
            seeded = true;
            return ema;
        }

        // Steady state
        ema = alpha * x + (1.0 - alpha) * ema;
        return ema;
    }

    /** True once EMA has a defined value. */
    public boolean isReady() { return seeded; }

    /** Current EMA value (may be NOT_DEFINED before ready). */
    public double value() { return ema; }

    /** Reset to initial state (keeps config). */
    public void reset() {
        count = 0;
        warmupSum = 0;
        seeded = false;
        ema = NOT_DEFINED;
    }
}