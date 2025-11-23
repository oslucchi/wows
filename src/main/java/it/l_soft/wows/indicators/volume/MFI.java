package it.l_soft.wows.indicators.volume;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Money Flow Index (MFI).
 *
 *  TP  = (High + Low + Close) / 3
 *  MF  = TP * Volume
 *
 *  If TP_t > TP_{t-1} => positive money flow
 *  If TP_t < TP_{t-1} => negative money flow
 *
 *  MFR = sum(positive MF) / sum(negative MF)
 *  MFI = 100 - 100 / (1 + MFR)
 *
 * - Uses ring buffers for positive/negative MF over the last 'period' bars.
 * - Returns NOT_DEFINED until 'period' bars have been processed.
 */
public final class MFI extends AbstractIndicator {

    private final int period;

    private final double[] posMF;
    private final double[] negMF;

    private int idx   = 0;
    private int count = 0;

    private double sumPos = 0.0;
    private double sumNeg = 0.0;

    private double prevTp = Double.NaN;
    private double lastMfi = NOT_DEFINED;

    public MFI(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.posMF  = new double[period];
        this.negMF  = new double[period];
    }

    // For factory signatures like (int, Price)
    public MFI(int period, Price unused) {
        this(period);
    }

    @Override
    public double add(Bar bar) {
        final double tp = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
        final double mf = tp * bar.getVolume();

        if (Double.isNaN(prevTp)) {
            prevTp = tp;
            lastMfi = NOT_DEFINED;
            return lastMfi;
        }

        double pos = 0.0;
        double neg = 0.0;
        if (tp > prevTp)      pos = mf;
        else if (tp < prevTp) neg = mf;

        if (count < period) {
            posMF[idx] = pos;
            negMF[idx] = neg;
            sumPos += pos;
            sumNeg += neg;
            idx++; if (idx == period) idx = 0;
            count++;

            prevTp = tp;

            if (count < period) {
                lastMfi = NOT_DEFINED;
                return lastMfi;
            }
        } else {
            // Remove oldest, add newest
            sumPos -= posMF[idx];
            sumNeg -= negMF[idx];

            posMF[idx] = pos;
            negMF[idx] = neg;

            sumPos += pos;
            sumNeg += neg;

            idx++; if (idx == period) idx = 0;
            prevTp = tp;
        }

        if (sumNeg == 0.0) {
            if (sumPos == 0.0) {
                lastMfi = 50.0; // no flow at all
            } else {
                lastMfi = 100.0; // all upward flow
            }
        } else {
            final double mfr = sumPos / sumNeg;
            lastMfi = 100.0 - (100.0 / (1.0 + mfr));
        }

        return lastMfi;
    }

    @Override
    public double value() { return lastMfi; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        sumPos = sumNeg = 0.0;
        prevTp = Double.NaN;
        lastMfi = NOT_DEFINED;
        for (int i = 0; i < period; i++) {
            posMF[i] = 0.0;
            negMF[i] = 0.0;
        }
    }

    public boolean isReady() { return count >= period; }
}
