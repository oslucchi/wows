package it.l_soft.wows.indicators.momentum;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Commodity Channel Index (CCI).
 *
 *  TP  = (High + Low + Close) / 3
 *  SMA = average(TP over period)
 *  MD  = mean absolute deviation of TP around SMA over period
 *  CCI = (TP_t - SMA) / (0.015 * MD)
 *
 * - Uses a ring buffer for the last 'period' typical prices.
 * - Returns NOT_DEFINED until 'period' bars have been processed.
 */
public final class CCI extends AbstractIndicator {

    private final int period;
    private final double[] tpWindow;

    private int idx   = 0;
    private int count = 0;
    private double sumTp = 0.0;

    private double lastCci = NOT_DEFINED;

    public CCI(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.tpWindow = new double[period];
    }

    // For factories that expect (int, Price)
    public CCI(int period, Price unused) {
        this(period);
    }

    @Override
    public double add(Bar bar) {
        final double tp = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;

        if (count < period) {
            tpWindow[idx] = tp;
            sumTp += tp;
            idx++; if (idx == period) idx = 0;
            count++;

            if (count < period) {
                lastCci = NOT_DEFINED;
                return lastCci;
            }
        } else {
            // Steady state: remove oldest, add newest
            final double oldest = tpWindow[idx];
            sumTp += tp - oldest;
            tpWindow[idx] = tp;
            idx++; if (idx == period) idx = 0;
        }

        final double smaTp = sumTp / period;

        // Mean absolute deviation around SMA
        double devSum = 0.0;
        for (int i = 0; i < period; i++) {
            devSum += Math.abs(tpWindow[i] - smaTp);
        }
        final double md = devSum / period;

        if (md == 0.0) {
            lastCci = 0.0; // flat market
        } else {
            lastCci = (tp - smaTp) / (0.015 * md);
        }

        return lastCci;
    }

    @Override
    public double value() { return lastCci; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        sumTp = 0.0;
        lastCci = NOT_DEFINED;
        for (int i = 0; i < period; i++) tpWindow[i] = 0.0;
    }

    public boolean isReady() { return count >= period; }
}
