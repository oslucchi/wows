package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.bands.Bollinger;
import it.l_soft.wows.indicators.bands.Donchian;
import it.l_soft.wows.indicators.bands.Keltner;
import it.l_soft.wows.indicators.composite.MACD;
import it.l_soft.wows.indicators.momentum.ROC;
import it.l_soft.wows.indicators.momentum.RSI;
import it.l_soft.wows.indicators.momentum.StochasticK;
import it.l_soft.wows.indicators.trend.EMA;
import it.l_soft.wows.indicators.trend.SMA;
import it.l_soft.wows.indicators.volatility.ATR;

final class SignalNormalizer {
    private SignalNormalizer() {}
    static double clamp50(double x) {
        return Math.max(-50.0, Math.min(50.0, x));
    }

    /**
     * Normalize indicator value to [-50, 50].
     * For some indicators we need the current bar (for price, ATR, band positions).
     * Provide a shared ATR (atrForScaling) already fed each bar to stabilize scaling.
     */
    static double normalize(Indicator ind, Bar bar, ATR atrForScaling, ApplicationProperties props) {
        final double v = ind.value();
        if (Double.isNaN(v)) return 0.0;

        // BOUNDED oscillators (easy)
        if (ind instanceof RSI)          return v - 50.0;           // RSI in [0,100] → [-50,50]
        if (ind instanceof StochasticK)  return v - 50.0;           // %K in [0,100] → [-50,50]

        if (ind instanceof Bollinger) {
            Bollinger b = (Bollinger) ind;
            double pB = b.percentB(bar.getClose()); // 0..100
            return Double.isNaN(pB) ? 0.0 : (pB - 50.0);
        }

        if (ind instanceof Keltner) {
            Keltner k = (Keltner) ind;
            double pct = k.percentKC(bar.getClose()); // 0..100
            return Double.isNaN(pct) ? 0.0 : (pct - 50.0);
        }

        if (ind instanceof Donchian) {
            Donchian d = (Donchian) ind;
            double upper = d.upper(), lower = d.lower();
            if (Double.isNaN(upper) || Double.isNaN(lower)) return 0.0;
            double range = upper - lower;
            if (range == 0.0) return 0.0;
            double pct = (bar.getClose() - lower) * 100.0 / range; // 0..100
            return clamp50(pct - 50.0);
        }

        // MOMENTUM (percent)
        if (ind instanceof ROC) {
            // ROC is already percentage. Clip to sensible bounds.
            return clamp50(v);
        }

        // MACD (use histogram; scale by ATR%)
        if (ind instanceof MACD) {
            MACD m = (MACD) ind;
            double hist = m.histogram();
            if (Double.isNaN(hist)) return 0.0;
            double atr = atrForScaling.value();
            if (Double.isNaN(atr) || atr == 0.0) return 0.0;
            // Normalize histogram by ATR, scale to about [-50,50]
            double z = (hist / atr) * 50.0 * props.getMacdToAtrScale();
            return clamp50(z);
        }

        // TREND (price vs MA), scale by ATR
        if (ind instanceof SMA || ind instanceof EMA) {
            double ma = v;
            double atr = atrForScaling.value();
            if (Double.isNaN(ma) || Double.isNaN(atr) || atr == 0.0) return 0.0;
            double diff = bar.getClose() - ma;
            double z = (diff / atr) * 25.0; // 25 to be conservative vs oscillators
            return clamp50(z);
        }

        // VOLATILITY-only (ATR): neutral signal (0). You can craft a rule later if needed.
        if (ind instanceof ATR) {
            return 0.0;
        }

        // Fallback: treat as z-ish using ATR as scale of typical move
        double atr = atrForScaling.value();
        if (!Double.isNaN(atr) && atr != 0.0) {
            return clamp50((v / atr) * 10.0);
        }
        return 0.0;
    }
}
