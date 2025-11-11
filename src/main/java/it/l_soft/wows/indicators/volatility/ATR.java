package it.l_soft.wows.indicators.volatility;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;

/**
 * Streaming ATR (Wilder's).
 *
 * Warm-up:
 *  - Accumulate True Range (TR) for the first 'period' bars.
 *  - Seed ATR as the simple average of those TRs.
 *
 * Steady state:
 *  - ATR_t = (ATR_{t-1} * (period - 1) + TR_t) / period
 *
 * Notes:
 *  - First bar's TR is defined as (high - low).
 *  - Returns Double.NaN until 'period' bars have been processed.
 *  - If period == 1, ATR equals TR each bar.
 */
public final class ATR implements Indicator {
    private int period = 0;

    // State
    private int barsSeen = 0;
    private double prevClose = Double.NaN;

    // Warm-up sum of TR for seeding
    private double sumTr = 0.0;
    private boolean seeded = false;

    // Current ATR value
    private double atr = NOT_DEFINED;

    public ATR(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }

    // Add this delegating ctor:
    public ATR(int period, Price unused) {
        this(period);
    }

    @Override
    public double add(Bar bar) {
        final double high = bar.getHigh();
        final double low  = bar.getLow();
        final double close = bar.getClose();

        // Compute True Range for this bar
        final double tr;
        if (barsSeen == 0 || Double.isNaN(prevClose)) {
            // First bar: TR = high - low
            tr = high - low;
        } else {
            // Subsequent bars: TR = max(high-low, |high-prevClose|, |low-prevClose|)
            final double hl = high - low;
            final double hp = Math.abs(high - prevClose);
            final double lp = Math.abs(low  - prevClose);
            tr = Math.max(hl, Math.max(hp, lp));
        }

        barsSeen++;

        if (!seeded) {
            // Accumulate TR for warm-up
            sumTr += tr;

            if (barsSeen < period) {
                // Need 'period' bars before ATR is defined
                prevClose = close;
                atr = NOT_DEFINED;
                return atr;
            }

            // barsSeen == period → seed ATR as SMA of TRs
            atr = sumTr / period;
            seeded = true;
            prevClose = close;
            return atr;
        }

        // Steady-state Wilder smoothing
        // If period == 1, this reduces to atr = tr
        atr = ((atr * (period - 1)) + tr) / period;
        prevClose = close;
        return atr;
    }

    @Override
    public double value() { return atr; }

    @Override
    public void reset() {
        barsSeen = 0;
        prevClose = Double.NaN;
        sumTr = 0.0;
        seeded = false;
        atr = NOT_DEFINED;
    }

    public boolean isReady() { return seeded; }
}
