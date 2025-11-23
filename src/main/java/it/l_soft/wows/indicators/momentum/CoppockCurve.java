package it.l_soft.wows.indicators.momentum;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;
import it.l_soft.wows.indicators.trend.WMA;

/**
 * Streaming Coppock Curve.
 *
 * Typical definition:
 *   ROC1 = ROC(price, longRocPeriod)
 *   ROC2 = ROC(price, shortRocPeriod)
 *   S    = ROC1 + ROC2
 *   Coppock = WMA(S, wmaPeriod)
 *
 * - Uses two streaming ROC instances plus a streaming WMA.
 * - Returns NOT_DEFINED until both ROCs and the WMA are ready.
 */
public final class CoppockCurve extends AbstractIndicator {

    private final ROC rocLong;
    private final ROC rocShort;
    private final WMA wma;

    private double coppock = NOT_DEFINED;

    public CoppockCurve(int longRocPeriod, int shortRocPeriod, int wmaPeriod, Price price) {
        if (longRocPeriod <= 0 || shortRocPeriod <= 0 || wmaPeriod <= 0) {
            throw new IllegalArgumentException("periods must be > 0");
        }
        this.rocLong = new ROC(longRocPeriod, price);
        this.rocShort = new ROC(shortRocPeriod, price);
        // WMA on scalar S series -> use close of synthetic bar
        this.wma = new WMA(wmaPeriod, (Bar b) -> b.getClose());
    }

    @Override
    public double add(Bar bar) {
        double rL = rocLong.add(bar);
        double rS = rocShort.add(bar);

        if (Double.isNaN(rL) || Double.isNaN(rS)) {
            coppock = NOT_DEFINED;
            return coppock;
        }

        double s = rL + rS;
        double smoothed = wma.add(scalarAsBar(s));

        if (Double.isNaN(smoothed)) {
            coppock = NOT_DEFINED;
        } else {
            coppock = smoothed;
        }
        return coppock;
    }

    @Override
    public double value() { return coppock; }

    @Override
    public void reset() {
        rocLong.reset();
        rocShort.reset();
        wma.reset();
        coppock = NOT_DEFINED;
    }

    public boolean isReady() { return !Double.isNaN(coppock); }

    private static Bar scalarAsBar(double x) {
        return new Bar() {
            @Override public long   getBarNumber() { return 0L; }
            @Override public double getOpen()      { return x; }
            @Override public double getHigh()      { return x; }
            @Override public double getLow()       { return x; }
            @Override public double getClose()     { return x; }
            @Override public long   getVolume()    { return 0L; }
        };
    }
}
