package it.l_soft.wows.indicators.momentum;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.Indicator;

import java.util.ArrayDeque;

/**
 * Streaming Stochastic %K (Fast %K):
 *   %K = 100 * (Close - LowestLow_n) / (HighestHigh_n - LowestLow_n)
 *
 * Rolling min/max maintained with monotonic deques (O(1) amortized).
 * Returns Double.NaN until 'period' bars processed.
 */
public final class StochasticK implements Indicator {
    private final int period;

    // Monotonic deques store (value, index) pairs; head is current min/max.
    private static final class Node { final double v; final int i; Node(double v, int i){this.v=v;this.i=i;} }
    private final ArrayDeque<Node> maxQ = new ArrayDeque<>(); // strictly decreasing highs
    private final ArrayDeque<Node> minQ = new ArrayDeque<>(); // strictly increasing lows

    private int index = -1;           // global bar index
    private int count = 0;            // bars seen (<= period)
    private double lastK = NOT_DEFINED;

    public StochasticK(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }
    
    // Add this so the factory's (int, Price) call works:
    public StochasticK(int period, Price unused) {
        this(period);
    }

    @Override
    public double add(Bar bar) {
        index++;

        final double high = bar.getHigh();
        final double low  = bar.getLow();
        final double close= bar.getClose();

        // push high to maxQ (keep decreasing)
        while (!maxQ.isEmpty() && maxQ.getLast().v <= high) maxQ.removeLast();
        maxQ.addLast(new Node(high, index));

        // push low to minQ (keep increasing)
        while (!minQ.isEmpty() && minQ.getLast().v >= low) minQ.removeLast();
        minQ.addLast(new Node(low, index));

        // drop items outside the window [index - period + 1 .. index]
        final int windowStart = index - period + 1;
        while (!maxQ.isEmpty() && maxQ.getFirst().i < windowStart) maxQ.removeFirst();
        while (!minQ.isEmpty() && minQ.getFirst().i < windowStart) minQ.removeFirst();

        // warm-up
        if (count < period) {
            count++;
            lastK = NOT_DEFINED;
            if (count < period) return lastK;
        }

        final double hh = maxQ.getFirst().v;
        final double ll = minQ.getFirst().v;
        final double range = hh - ll;

        if (range == 0.0) {
            // Flat window; choose 50.0 as neutral (common choice).
            // Alternatively, set to 0 or carry-forward previous value.
            lastK = 50.0;
        } else {
            lastK = 100.0 * (close - ll) / range;
        }
        return lastK;
    }

    @Override public double value() { return lastK; }

    @Override
    public void reset() {
        maxQ.clear();
        minQ.clear();
        index = -1;
        count = 0;
        lastK = NOT_DEFINED;
    }

    public boolean isReady() { return count >= period; }
}
