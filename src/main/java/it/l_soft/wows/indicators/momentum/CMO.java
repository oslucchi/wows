package it.l_soft.wows.indicators.momentum;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Chande Momentum Oscillator (CMO).
 *
 *  Δ  = price_t - price_{t-1}
 *  up   = max(Δ, 0)
 *  down = max(-Δ, 0)
 *
 *  CMO = 100 * (sum(up) - sum(down)) / (sum(up) + sum(down))
 *
 * - Uses ring buffers for up/down over the last 'period' deltas.
 * - Returns NOT_DEFINED until 'period' deltas (i.e. period+1 prices) available.
 */
public final class CMO extends AbstractIndicator {

    private final int period;
    private final ToDoubleFunction<Bar> input;

    private final double[] ups;
    private final double[] downs;

    private int idx   = 0;          // next slot
    private int count = 0;          // number of stored deltas (<= period)
    private double sumUp   = 0.0;
    private double sumDown = 0.0;

    private double prevPrice = Double.NaN;
    private double lastCmo   = NOT_DEFINED;

    /** Standard CMO on chosen price (e.g., CLOSE). */
    public CMO(int period, Price price) {
        this(period, price.extractor);
    }

    /** CMO fed by a custom extractor. */
    public CMO(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.input  = input;
        this.ups    = new double[period];
        this.downs  = new double[period];
    }

    @Override
    public double add(Bar bar) {
        final double p = input.applyAsDouble(bar);

        if (Double.isNaN(prevPrice)) {
            prevPrice = p;
            lastCmo = NOT_DEFINED;
            return lastCmo;
        }

        final double delta = p - prevPrice;
        final double up    = delta > 0 ?  delta : 0.0;
        final double down  = delta < 0 ? -delta : 0.0;

        if (count < period) {
            ups[idx]   = up;
            downs[idx] = down;
            sumUp   += up;
            sumDown += down;
            idx++; if (idx == period) idx = 0;
            count++;

            if (count < period) {
                prevPrice = p;
                lastCmo = NOT_DEFINED;
                return lastCmo;
            }
        } else {
            // Remove oldest, add newest
            sumUp   -= ups[idx];
            sumDown -= downs[idx];

            ups[idx]   = up;
            downs[idx] = down;

            sumUp   += up;
            sumDown += down;

            idx++; if (idx == period) idx = 0;
        }

        prevPrice = p;

        final double denom = sumUp + sumDown;
        if (denom == 0.0) {
            lastCmo = 0.0; // no net momentum
        } else {
            lastCmo = 100.0 * (sumUp - sumDown) / denom;
        }

        return lastCmo;
    }

    @Override
    public double value() { return lastCmo; }

    @Override
    public void reset() {
        idx = 0;
        count = 0;
        sumUp = sumDown = 0.0;
        prevPrice = Double.NaN;
        lastCmo = NOT_DEFINED;
        for (int i = 0; i < period; i++) {
            ups[i] = 0.0;
            downs[i] = 0.0;
        }
    }

    public boolean isReady() { return count >= period; }
}
