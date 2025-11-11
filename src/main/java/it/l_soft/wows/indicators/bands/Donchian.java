package it.l_soft.wows.indicators.bands;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.Indicator;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Streaming Donchian Channel (trend-following / breakout indicator)
 *
 * upper = highest high over N bars
 * lower = lowest low over N bars
 * mid   = (upper + lower) / 2
 *
 * Returns NOT_DEFINED until at least N bars are seen.
 */
public final class Donchian implements Indicator {
    private final int period;
    private final Deque<Double> highs;
    private final Deque<Double> lows;

    private double upper = NOT_DEFINED;
    private double lower = NOT_DEFINED;
    private double mid   = NOT_DEFINED;

    private int barsSeen = 0;

    public Donchian(int period) {
        if (period <= 0)
            throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.highs = new ArrayDeque<>(period);
        this.lows  = new ArrayDeque<>(period);
    }

    @Override
    public double add(Bar bar) {
        double high = bar.getHigh();
        double low  = bar.getLow();

        // maintain fixed-length deque
        if (highs.size() == period) highs.removeFirst();
        if (lows.size()  == period) lows.removeFirst();
        highs.addLast(high);
        lows.addLast(low);

        barsSeen++;
        if (barsSeen < period) {
            upper = lower = mid = NOT_DEFINED;
            return mid;
        }

        // recompute from window (O(period), acceptable for small N)
        upper = highs.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        lower = lows.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        mid   = (upper + lower) / 2.0;

        return mid;
    }

    @Override
    public double value() { return mid; }

    public double upper() { return upper; }
    public double lower() { return lower; }

    @Override
    public void reset() {
        highs.clear();
        lows.clear();
        barsSeen = 0;
        upper = lower = mid = NOT_DEFINED;
    }
}
