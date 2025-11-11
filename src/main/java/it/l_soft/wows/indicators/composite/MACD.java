package it.l_soft.wows.indicators.composite;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.trend.EMA;

/**
 * Streaming MACD:
 *   MACD  = EMA_fast(price) - EMA_slow(price)
 *   Signal= EMA_signal(MACD)
 *   Hist  = MACD - Signal
 *
 * - add(Bar) one at a time
 * - O(1) state, no history arrays
 * - value() returns MACD line
 * - getSignal(), getHistogram() available
 *
 * Warm-up:
 * - MACD is NaN until both fast/slow EMAs are defined.
 * - Signal/Histogram are NaN until signal EMA is defined.
 *
 * Typical defaults: fast=12, slow=26, signal=9 on Price.CLOSE.
 */
public final class MACD implements Indicator {
    private final EMA emaFast;
    private final EMA emaSlow;
    private final int signalPeriod;

    // We'll smooth the MACD line with an EMA driven by synthetic Bars
    private final EMA emaSignal;

    private double macd = NOT_DEFINED;
    private double signal = NOT_DEFINED;
    private double hist = NOT_DEFINED;

    /** Standard MACD with a Price selector (e.g., Price.CLOSE). */
    public MACD(int fastPeriod, int slowPeriod, int signalPeriod, Price price) {
        this(fastPeriod, slowPeriod, signalPeriod, price.extractor);
    }

    /** MACD fed by a custom extractor from Bar to double. */
    public MACD(int fastPeriod, int slowPeriod, int signalPeriod, ToDoubleFunction<Bar> input) {
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
            throw new IllegalArgumentException("periods must be > 0");
        }
        // Ensure conventional ordering (fast < slow); swap if needed
        if (fastPeriod > slowPeriod) {
            int tmp = fastPeriod; fastPeriod = slowPeriod; slowPeriod = tmp;
        }

        this.signalPeriod = signalPeriod;
        this.emaFast   = new EMA(fastPeriod, input);
        this.emaSlow   = new EMA(slowPeriod, input);
        // Signal EMA runs over MACD values; we feed it via a tiny scalar->Bar adapter
        this.emaSignal = new EMA(signalPeriod, (ToDoubleFunction<Bar>) b -> b.getClose());
    }

    /** Convenience: no Price provided -> defaults to CLOSE. */
    public MACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        this(fastPeriod, slowPeriod, signalPeriod, Price.CLOSE);
    }

    @Override
    public double add(Bar bar) {
        final double f = emaFast.add(bar);
        final double s = emaSlow.add(bar);

        if (Double.isNaN(f) || Double.isNaN(s)) {
            // Fast/slow not ready yet
            macd = NOT_DEFINED;
            signal = NOT_DEFINED;
            hist = NOT_DEFINED;
            return macd;
        }

        macd = f - s;

        // Feed MACD into signal EMA by wrapping it into a synthetic Bar
        final double sig = emaSignal.add(scalarAsBar(macd));

        if (Double.isNaN(sig)) {
            signal = NOT_DEFINED;
            hist = NOT_DEFINED;
        } else {
            signal = sig;
            hist = macd - signal;
        }

        return macd;
    }

    @Override
    public double value() { return macd; }

    /** Signal line (EMA over MACD). May be NaN until warmed up. */
    public double signal() { return signal; }

    /** Histogram = MACD - Signal. May be NaN until warmed up. */
    public double histogram() { return hist; }

    @Override
    public void reset() {
        emaFast.reset();
        emaSlow.reset();
        emaSignal.reset();
        macd = signal = hist = NOT_DEFINED;
    }

    public boolean isMacdReady() { return !Double.isNaN(macd); }
    public boolean isSignalReady() { return !Double.isNaN(signal); }

    // --- tiny helper: wrap a scalar into a Bar so our EMA can consume it ---
    private static Bar scalarAsBar(double x) {
        return new Bar() {
            @Override public double getOpen()  { return x; }
            @Override public double getHigh()  { return x; }
            @Override public double getLow()   { return x; }
            @Override public double getClose() { return x; }
            @Override public long   getVolume(){ return 0L; }
//            @Override public long   getTimestamp(){ return 0L; }
        };
    }
}
