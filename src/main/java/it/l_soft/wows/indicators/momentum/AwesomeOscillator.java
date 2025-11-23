package it.l_soft.wows.indicators.momentum;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.AbstractIndicator;
import it.l_soft.wows.indicators.trend.SMA;

/**
 * Streaming Awesome Oscillator (AO).
 *
 * AO = SMA(medianPrice, shortPeriod) - SMA(medianPrice, longPeriod)
 * where medianPrice = (high + low) / 2.
 *
 * - Uses your streaming SMA implementation.
 * - Returns NOT_DEFINED until both SMAs are ready.
 */
public final class AwesomeOscillator extends AbstractIndicator {

    private final int shortPeriod;
    private final int longPeriod;
    private final SMA smaShort;
    private final SMA smaLong;

    private double ao = NOT_DEFINED;

    public AwesomeOscillator(int shortPeriod, int longPeriod) {
        if (shortPeriod <= 0 || longPeriod <= 0) {
            throw new IllegalArgumentException("periods must be > 0");
        }
        if (shortPeriod > longPeriod) {
            int tmp = shortPeriod;
            shortPeriod = longPeriod;
            longPeriod = tmp;
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod  = longPeriod;

        ToDoubleFunction<Bar> medianPrice = b -> (b.getHigh() + b.getLow()) / 2.0;
        this.smaShort = new SMA(shortPeriod, medianPrice);
        this.smaLong  = new SMA(longPeriod,  medianPrice);
    }

    @Override
    public double add(Bar bar) {
        double s = smaShort.add(bar);
        double l = smaLong.add(bar);

        if (Double.isNaN(s) || Double.isNaN(l)) {
            ao = NOT_DEFINED;
        } else {
            ao = s - l;
        }
        return ao;
    }

    @Override
    public double value() { return ao; }

    @Override
    public void reset() {
        smaShort.reset();
        smaLong.reset();
        ao = NOT_DEFINED;
    }

    public boolean isReady() {
        return !Double.isNaN(ao);
    }

    public int getShortPeriod() { return shortPeriod; }
    public int getLongPeriod()  { return longPeriod; }
}
