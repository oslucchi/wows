package it.l_soft.wows.indicators.momentum;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;
import it.l_soft.wows.indicators.trend.EMA;

/**
 * Streaming TRIX (Triple Exponential Average Rate of Change).
 *
 *  EMA1 = EMA(price, period)
 *  EMA2 = EMA(EMA1, period)
 *  EMA3 = EMA(EMA2, period)
 *
 *  TRIX_t = 100 * (EMA3_t - EMA3_{t-1}) / EMA3_{t-1}
 *
 * - Uses your EMA class three times.
 * - Returns NOT_DEFINED until EMA3 is defined for two consecutive bars.
 */
public final class TRIX extends AbstractIndicator {

    private final int period;
    private final ToDoubleFunction<Bar> input;

    private final EMA ema1;
    private final EMA ema2;
    private final EMA ema3;

    private double prevEma3 = Double.NaN;
    private double trix = NOT_DEFINED;

    /** TRIX on a chosen price (e.g., Price.CLOSE). */
    public TRIX(int period, Price price) {
        this(period, price.extractor);
    }

    /** TRIX with custom input extractor. */
    public TRIX(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.input  = input;

        // EMA1 directly on input
        this.ema1 = new EMA(period, input);
        // EMA2 and EMA3 on scalar series (we use close of synthetic bars)
        this.ema2 = new EMA(period, (Bar b) -> b.getClose());
        this.ema3 = new EMA(period, (Bar b) -> b.getClose());
    }

    @Override
    public double add(Bar bar) {
        final double x = input.applyAsDouble(bar);

        double e1 = ema1.add(bar);
        if (Double.isNaN(e1)) {
            trix = NOT_DEFINED;
            return trix;
        }

        double e2 = ema2.add(scalarAsBar(e1));
        if (Double.isNaN(e2)) {
            trix = NOT_DEFINED;
            return trix;
        }

        double e3 = ema3.add(scalarAsBar(e2));
        if (Double.isNaN(e3)) {
            trix = NOT_DEFINED;
            return trix;
        }

        if (Double.isNaN(prevEma3) || e3 == 0.0) {
            prevEma3 = e3;
            trix = NOT_DEFINED;
            return trix;
        }

        trix = 100.0 * (e3 - prevEma3) / prevEma3;
        prevEma3 = e3;
        return trix;
    }

    @Override
    public double value() { return trix; }

    @Override
    public void reset() {
        ema1.reset();
        ema2.reset();
        ema3.reset();
        prevEma3 = Double.NaN;
        trix = NOT_DEFINED;
    }

    public boolean isReady() { return !Double.isNaN(trix); }

    // Same helper pattern as in MACD
    private static Bar scalarAsBar(double x) {
        return new Bar() {
            @Override public long   getBarNumber() { return 0L; }
            @Override public double getOpen()      { return x; }
            @Override public double getHigh()      { return x; }
            @Override public double getLow()       { return x; }
            @Override public double getClose()     { return x; }
            @Override public long   getVolume()    { return 0L; }
            @Override public long   getTimestamp()    { return 0L; }
        };
    }
}
