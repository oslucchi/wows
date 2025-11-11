package it.l_soft.wows.indicators.volume;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.Indicator;

/**
 * Streaming On-Balance Volume (OBV).
 * obv_t = obv_{t-1} + sign(close_t - close_{t-1}) * volume_t
 * Returns NaN until a prior close exists.
 */
public final class OBV implements Indicator {
    private long  lastVolume = 0L;         // not strictly needed, but kept for clarity
    private double lastClose = Double.NaN; // prior close
    private double obv = NOT_DEFINED;

    @Override
    public double add(Bar bar) {
        final double close = bar.getClose();
        final long   vol   = bar.getVolume();
        if (Double.isNaN(lastClose)) {
            // first bar: need a previous close to define direction
            lastClose = close;
            obv = NOT_DEFINED;
            return obv;
        }
        long delta = 0L;
        if (close > lastClose)      delta =  vol;
        else if (close < lastClose) delta = -vol;
        // if equal, delta = 0
        obv = Double.isNaN(obv) ? delta : (obv + delta);

        lastClose = close;
        lastVolume = vol;
        return obv;
    }

    @Override public double value() { return obv; }

    @Override
    public void reset() {
        lastClose = Double.NaN;
        lastVolume = 0L;
        obv = NOT_DEFINED;
    }
}
