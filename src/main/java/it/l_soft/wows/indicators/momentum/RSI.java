package it.l_soft.wows.indicators.momentum;

import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;

/**
 * Streaming RSI (Wilder's) with O(1) memory.
 * - Warmup: accumulate gains/losses for 'period' bars to seed avgGain/avgLoss.
 * - After warmup: Wilder smoothing:
 *      avgGain = (prevAvgGain * (period - 1) + gain) / period
 *      avgLoss = (prevAvgLoss * (period - 1) + loss) / period
 * - Returns Double.NaN until ready.
 */
public final class RSI implements Indicator {
    private final int period;
    private final ToDoubleFunction<Bar> input;

    // State
    private int count = 0;              // bars seen (including first seed bar)
    private double prevPrice = Double.NaN;
    private double sumGain = 0.0;       // during warmup
    private double sumLoss = 0.0;       // during warmup
    private double avgGain = 0.0;       // after warmup
    private double avgLoss = 0.0;       // after warmup
    private boolean seeded = false;
    private double rsi = NOT_DEFINED;

    /** Standard RSI on chosen price (e.g. Price.CLOSE). */
    public RSI(int period, Price price) {
        this(period, price.extractor);
    }

    /** RSI fed by a custom extractor. */
    public RSI(int period, ToDoubleFunction<Bar> input) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.input = input;
    }

    @Override
    public double add(Bar bar) {
        final double p = input.applyAsDouble(bar);

        // First bar: just initialize previous price
        if (count == 0) {
            prevPrice = p;
            count = 1;
            rsi = NOT_DEFINED;
            return rsi;
        }

        // Delta from prior close
        final double delta = p - prevPrice;
        final double gain  = delta > 0 ?  delta : 0.0;
        final double loss  = delta < 0 ? -delta : 0.0;

        if (!seeded) {
            // Warmup: accumulate gains/losses over first 'period' deltas
            sumGain += gain;
            sumLoss += loss;
            count++;

            if (count <= period) {
                // Not enough deltas yet (need exactly 'period' deltas = period+1 prices)
                prevPrice = p;
                rsi = NOT_DEFINED;
                return rsi;
            }

            // Seed averages at the first time we have 'period' deltas
            avgGain = sumGain / period;
            avgLoss = sumLoss / period;
            seeded = true;

        } else {
            // Wilder smoothing
            avgGain = ((avgGain * (period - 1)) + gain) / period;
            avgLoss = ((avgLoss * (period - 1)) + loss) / period;
        }

        prevPrice = p;

        // Compute RSI safely
        if (avgLoss == 0.0 && avgGain == 0.0) {
            rsi = 50.0;                // flat market
        } else if (avgLoss == 0.0) {
            rsi = 100.0;               // no losses
        } else {
            final double rs = avgGain / avgLoss;
            rsi = 100.0 - (100.0 / (1.0 + rs));
        }
        return rsi;
    }

    @Override
    public double value() { return rsi; }

    @Override
    public void reset() {
        count = 0;
        prevPrice = Double.NaN;
        sumGain = sumLoss = 0.0;
        avgGain = avgLoss = 0.0;
        seeded = false;
        rsi = NOT_DEFINED;
    }

    public boolean isReady() { return seeded; }
}
