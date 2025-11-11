package it.l_soft.wows.indicators.composite;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.momentum.StochasticK;
import it.l_soft.wows.indicators.trend.SMA;

public final class StochasticD implements Indicator {
    private final StochasticK k;
    private final SMA d; // SMA over %K

    private double lastD = NOT_DEFINED;

    public StochasticD(int kPeriod, int dPeriod) {
        this.k = new StochasticK(kPeriod);
        // SMA over a synthetic Bar carrying %K as 'close'
        this.d = new SMA(dPeriod, b -> b.getClose()); // we will feed pseudo-bars
    }

    @Override
    public double add(Bar bar) {
        double kVal = k.add(bar);
        if (Double.isNaN(kVal)) {
            lastD = NOT_DEFINED;
            return lastD;
        }
        // Wrap %K into a tiny Bar-like adapter so Sma can consume it
        Bar kBar = new Bar() {
            public double getOpen()  { return kVal; }
            public double getHigh()  { return kVal; }
            public double getLow()   { return kVal; }
            public double getClose() { return kVal; }
            public long   getVolume(){ return 0L;   }
            public long   getTimestamp(){ return 0L; }
        };
        lastD = d.add(kBar);
        return lastD;
    }

    @Override public double value() { return lastD; }
    @Override public void reset() { k.reset(); d.reset(); lastD = NOT_DEFINED; }
    public boolean isReady() { return !Double.isNaN(lastD); }
}
