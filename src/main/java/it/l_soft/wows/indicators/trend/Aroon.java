package it.l_soft.wows.indicators.trend;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Aroon:
 *
 *  AroonUp   = 100 * (period - barsSinceHighestHigh) / period
 *  AroonDown = 100 * (period - barsSinceLowestLow)  / period
 *  Oscillator= AroonUp - AroonDown
 *
 * - Uses fixed-size circular buffers for high/low and their bar indices.
 * - Returns NOT_DEFINED until at least 'period' bars have been processed.
 *
 * value() returns the oscillator (Up - Down); up() / down() expose the components.
 */
public final class Aroon extends AbstractIndicator {

    private final int period;

    private final double[] highs;
    private final double[] lows;
    private final int[]    idxs; // bar index for each slot

    private int barIndex = -1;   // global bar counter
    private int count    = 0;    // how many bars we've actually seen (<= period)

    private double up   = NOT_DEFINED;
    private double down = NOT_DEFINED;
    private double osc  = NOT_DEFINED;

    public Aroon(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.highs  = new double[period];
        this.lows   = new double[period];
        this.idxs   = new int[period];
        resetBuffers();
    }

    // For factory signatures that pass (int, Price) even if not used
    public Aroon(int period, Price unused) {
        this(period);
    }

    @Override
    public double add(Bar bar) {
        barIndex++;

        final double high = bar.getHigh();
        final double low  = bar.getLow();

        final int slot = barIndex % period;
        highs[slot] = high;
        lows[slot]  = low;
        idxs[slot]  = barIndex;

        if (count < period) {
            count++;
            if (count < period) {
                up = down = osc = NOT_DEFINED;
                return osc;
            }
        }

        // We have at least 'period' bars; compute AroonUp/Down over the last `period`.
        final int windowStart = barIndex - period + 1;

        double hh = Double.NEGATIVE_INFINITY;
        int hhIndex = -1;
        double ll = Double.POSITIVE_INFINITY;
        int llIndex = -1;

        for (int i = 0; i < period; i++) {
            int idx = idxs[i];
            if (idx < windowStart) continue; // outside window

            double h = highs[i];
            double l = lows[i];

            if (h >= hh) {
                hh = h;
                hhIndex = idx;
            }
            if (l <= ll) {
                ll = l;
                llIndex = idx;
            }
        }

        if (hhIndex < 0 || llIndex < 0) {
            up = down = osc = NOT_DEFINED;
            return osc;
        }

        final int barsSinceHH = barIndex - hhIndex;
        final int barsSinceLL = barIndex - llIndex;

        up   = 100.0 * (period - barsSinceHH) / period;
        down = 100.0 * (period - barsSinceLL) / period;
        osc  = up - down;

        return osc;
    }

    @Override
    public double value() { return osc; }

    public double up()   { return up; }
    public double down() { return down; }

    @Override
    public void reset() {
        barIndex = -1;
        count    = 0;
        up = down = osc = NOT_DEFINED;
        resetBuffers();
    }

    private void resetBuffers() {
        for (int i = 0; i < period; i++) {
            highs[i] = 0.0;
            lows[i]  = 0.0;
            idxs[i]  = -1;
        }
    }

    public boolean isReady() { return count >= period; }
}
