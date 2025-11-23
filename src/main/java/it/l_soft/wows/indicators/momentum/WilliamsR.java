package it.l_soft.wows.indicators.momentum;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

import java.util.ArrayDeque;

/**
 * Streaming Williams %R:
 *
 *   %R = -100 * (HH_n - Close) / (HH_n - LL_n)
 *
 * - Uses monotonic deques (like StochasticK) to track rolling high/low in O(1) amortized time.
 * - Returns NOT_DEFINED until 'period' bars have been processed.
 */
public final class WilliamsR extends AbstractIndicator {

    private final int period;

    // Monotonic deques store (value, index) pairs; head is current min/max.
    private static final class Node {
        final double v;
        final int i;
        Node(double v, int i) { this.v = v; this.i = i; }
    }

    private final ArrayDeque<Node> maxQ = new ArrayDeque<>(); // decreasing highs
    private final ArrayDeque<Node> minQ = new ArrayDeque<>(); // increasing lows

    private int index   = -1;
    private int count   = 0;
    private double lastR = NOT_DEFINED;

    public WilliamsR(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }

    // For factories that expect (int, Price)
    public WilliamsR(int period, Price unused) {
        this(period);
    }

    @Override
    public double add(Bar bar) {
        index++;

        final double high  = bar.getHigh();
        final double low   = bar.getLow();
        final double close = bar.getClose();

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

        if (count < period) {
            count++;
            lastR = NOT_DEFINED;
            if (count < period) return lastR;
        }

        final double hh = maxQ.getFirst().v;
        final double ll = minQ.getFirst().v;
        final double range = hh - ll;

        if (range == 0.0) {
            // Flat window; neutral-ish value
            lastR = -50.0;
        } else {
            lastR = -100.0 * (hh - close) / range;
        }
        return lastR;
    }

    @Override
    public double value() { return lastR; }

    @Override
    public void reset() {
        maxQ.clear();
        minQ.clear();
        index = -1;
        count = 0;
        lastR = NOT_DEFINED;
    }

    public boolean isReady() { return count >= period; }
}
