package it.l_soft.wows.indicators.trend;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.ToDoubleFunction;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.comms.Price;
import it.l_soft.wows.indicators.AbstractIndicator;

/**
 * Streaming Kaufman Adaptive Moving Average (KAMA).
 *
 * ER  = |price_t - price_{t-erPeriod}| / sum_{i=t-erPeriod+1..t} |price_i - price_{i-1}|
 * fastSC = 2 / (fastPeriod + 1)
 * slowSC = 2 / (slowPeriod + 1)
 * SC  = (ER * (fastSC - slowSC) + slowSC)^2
 * KAMA_t = KAMA_{t-1} + SC * (price_t - KAMA_{t-1})
 *
 * - Uses an ER lookback of 'erPeriod'.
 * - Returns NOT_DEFINED until we have at least erPeriod+1 prices and a seeded KAMA.
 */
public final class KAMA extends AbstractIndicator {

    private final int erPeriod;
    private final double fastSC;
    private final double slowSC;
    private final ToDoubleFunction<Bar> input;

    // Last erPeriod+1 prices (for ER numerator/denominator)
    private final Deque<Double> prices;

    private double kama = NOT_DEFINED;
    private boolean seeded = false;

    /** Standard KAMA on a chosen price (e.g., Price.CLOSE). */
    public KAMA(int erPeriod, int fastPeriod, int slowPeriod, Price price) {
        this(erPeriod, fastPeriod, slowPeriod, price.extractor);
    }

    /** KAMA with custom input extractor. */
    public KAMA(int erPeriod, int fastPeriod, int slowPeriod, ToDoubleFunction<Bar> input) {
        if (erPeriod <= 0 || fastPeriod <= 0 || slowPeriod <= 0) {
            throw new IllegalArgumentException("periods must be > 0");
        }
        this.erPeriod = erPeriod;
        this.input    = input;
        this.prices   = new ArrayDeque<>(erPeriod + 1);

        double fast = 2.0 / (fastPeriod + 1.0);
        double slow = 2.0 / (slowPeriod + 1.0);
        this.fastSC = fast;
        this.slowSC = slow;
    }

    /** Convenience: KAMA with Price.CLOSE. */
    public KAMA(int erPeriod, int fastPeriod, int slowPeriod) {
        this(erPeriod, fastPeriod, slowPeriod, Price.CLOSE);
    }

    @Override
    public double add(Bar bar) {
        final double p = input.applyAsDouble(bar);

        prices.addLast(p);
        if (prices.size() > erPeriod + 1) {
            prices.removeFirst();
        }

        // Need at least erPeriod+1 prices to compute ER
        if (prices.size() < erPeriod + 1) {
            kama = NOT_DEFINED;
            return kama;
        }

        // Compute ER: numerator = |p_t - p_{t-erPeriod}|, denominator = sum(|Δ|)
        double first = prices.peekFirst();
        double last  = prices.peekLast();
        double numerator = Math.abs(last - first);

        double denom = 0.0;
        Double prev = null;
        for (double x : prices) {
            if (prev != null) {
                denom += Math.abs(x - prev);
            }
            prev = x;
        }

        double er = (denom == 0.0) ? 0.0 : (numerator / denom);
        double sc = er * (fastSC - slowSC) + slowSC;
        sc = sc * sc; // squared

        if (!seeded) {
            // Seed KAMA with first available price once ER is defined
            kama = p;
            seeded = true;
        } else {
            kama = kama + sc * (p - kama);
        }

        return kama;
    }

    @Override
    public double value() { return kama; }

    @Override
    public void reset() {
        prices.clear();
        kama = NOT_DEFINED;
        seeded = false;
    }

    public boolean isReady() {
        return seeded && !Double.isNaN(kama);
    }

    public int getErPeriod() { return erPeriod; }
}
