package it.l_soft.wows.indicators.volatility;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.AbstractIndicator;
import it.l_soft.wows.indicators.trend.EMA;

/**
 * Streaming Mass Index.
 *
 * Let HL_t  = high_t - low_t.
 * EMA1_t = EMA(HL_t, emaPeriod)
 * EMA2_t = EMA(EMA1_t, emaPeriod)   (EMA of EMA1)
 * ratio_t = EMA1_t / EMA2_t
 * MassIndex_t = sum_{i=t-miPeriod+1..t} ratio_i
 *
 * - Uses two EMA instances.
 * - Uses a ring buffer of the last 'miPeriod' ratios.
 * - Returns NOT_DEFINED until both EMAs are ready and we have miPeriod ratios.
 */
public final class MassIndex extends AbstractIndicator {

    private final int emaPeriod;
    private final int miPeriod;

    private final EMA ema1;
    private final EMA ema2;

    private final double[] ratios;
    private int idx   = 0;
    private int count = 0;
    private double sumRatios = 0.0;

    private double massIndex = NOT_DEFINED;

    public MassIndex(int emaPeriod, int miPeriod) {
        if (emaPeriod <= 0 || miPeriod <= 0) {
            throw new IllegalArgumentException("periods must be > 0");
        }
        this.emaPeriod = emaPeriod;
        this.miPeriod  = miPeriod;

        // EMA of HL
        this.ema1 = new EMA(emaPeriod, (Bar b) -> b.getHigh() - b.getLow());
        // EMA of EMA1 (we feed it via scalarAsBar)
        this.ema2 = new EMA(emaPeriod, (Bar b) -> b.getClose());

        this.ratios = new double[miPeriod];
    }

    @Override
    public double add(Bar bar) {
        double e1 = ema1.add(bar);
        if (Double.isNaN(e1)) {
            massIndex = NOT_DEFINED;
            return massIndex;
        }

        double e2 = ema2.add(scalarAsBar(e1));
        if (Double.isNaN(e2) || e2 == 0.0) {
            massIndex = NOT_DEFINED;
            return massIndex;
        }

        double ratio = e1 / e2;

        if (count < miPeriod) {
            ratios[idx++] = ratio;
            sumRatios += ratio;
            count++;
            if (idx == miPeriod) idx = 0;

            if (count < miPeriod) {
                massIndex = NOT_DEFINED;
                return massIndex;
            }
        } else {
            // remove oldest, add newest
            sumRatios -= ratios[idx];
            ratios[idx] = ratio;
            sumRatios += ratio;
            idx++;
            if (idx == miPeriod) idx = 0;
        }

        massIndex = sumRatios;
        return massIndex;
    }

    @Override
    public double value() { return massIndex; }

    @Override
    public void reset() {
        ema1.reset();
        ema2.reset();
        idx = 0;
        count = 0;
        sumRatios = 0.0;
        massIndex = NOT_DEFINED;
        for (int i = 0; i < miPeriod; i++) ratios[i] = 0.0;
    }

    public boolean isReady() { return count >= miPeriod && !Double.isNaN(massIndex); }

    public int getEmaPeriod() { return emaPeriod; }
    public int getMiPeriod()  { return miPeriod; }

    // Helper: wrap a scalar into a Bar, same pattern as MACD.scalarAsBar
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
