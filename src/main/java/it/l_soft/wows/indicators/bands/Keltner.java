package it.l_soft.wows.indicators.bands;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.trend.EMA;
import it.l_soft.wows.indicators.volatility.ATR;

/**
 * Streaming Keltner Channels (modern):
 *   Mid   = EMA(midPeriod, price)
 *   Upper = Mid + multiplier * ATR(atrPeriod)
 *   Lower = Mid - multiplier * ATR(atrPeriod)
 *
 * Notes:
 * - Returns Double.NaN (NOT_DEFINED) until both EMA and ATR are defined.
 * - Typical defaults: period=20, atrPeriod=20, multiplier=2.0, price=CLOSE.
 */
public final class Keltner implements Indicator {
    private final EMA emaMid;
    private final ATR atr;
    private final double multiplier;

    private double mid = NOT_DEFINED;
    private double up  = NOT_DEFINED;
    private double low = NOT_DEFINED;

    /** Modern Keltner: EMA for middle, ATR for band width. */
    public Keltner(int midPeriod, int atrPeriod, double multiplier, Price price) {
        this(multiplier, new EMA(midPeriod, price), new ATR(atrPeriod));
    }

    /** Same period for EMA and ATR, with Price. */
    public Keltner(int period, double multiplier, Price price) {
        this(multiplier, new EMA(period, price), new ATR(period));
    }

    /** Use a custom price extractor for the EMA midline. */
    public Keltner(int midPeriod, int atrPeriod, double multiplier, ToDoubleFunction<Bar> input) {
        this(multiplier, new EMA(midPeriod, input), new ATR(atrPeriod));
    }

    private Keltner(double multiplier, EMA emaMid, ATR atr) {
        if (multiplier < 0) throw new IllegalArgumentException("multiplier must be >= 0");
        this.multiplier = multiplier;
        this.emaMid = emaMid;
        this.atr = atr;
    }

    /** Convenience: defaults to CLOSE, period=20, atrPeriod=20, multiplier=2.0 */
    public Keltner() {
        this(20, 20, 2.0, Price.CLOSE);
    }

    @Override
    public double add(Bar bar) {
        final double m = emaMid.add(bar);
        final double a = atr.add(bar);

        if (Double.isNaN(m) || Double.isNaN(a)) {
            mid = up = low = NOT_DEFINED;
            return mid;
        }

        mid = m;
        up  = m + multiplier * a;
        low = m - multiplier * a;
        return mid; // value() returns middle line
    }

    @Override public double value() { return mid; }
    public double upper() { return up; }
    public double lower() { return low; }

    /** Channel width normalized by mid; NaN if mid undefined or zero. */
    public double bandwidth() {
        if (Double.isNaN(up) || Double.isNaN(low) || Double.isNaN(mid) || mid == 0.0) return NOT_DEFINED;
        return (up - low) / mid;
    }

    /** Position within channel: 0=lower, 50=middle, 100=upper. */
    public double percentKC(double lastPrice) {
        if (Double.isNaN(up) || Double.isNaN(low)) return NOT_DEFINED;
        double range = up - low;
        if (range == 0.0) return 50.0;
        return 100.0 * (lastPrice - low) / range;
    }

    @Override
    public void reset() {
        emaMid.reset();
        atr.reset();
        mid = up = low = NOT_DEFINED;
    }
}
