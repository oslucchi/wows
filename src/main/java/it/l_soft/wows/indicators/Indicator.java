package it.l_soft.wows.indicators;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.bands.Bollinger;
import it.l_soft.wows.indicators.bands.Donchian;
import it.l_soft.wows.indicators.bands.Keltner;
import it.l_soft.wows.indicators.composite.MACD;
import it.l_soft.wows.indicators.composite.StochasticD;
import it.l_soft.wows.indicators.momentum.AwesomeOscillator;
import it.l_soft.wows.indicators.momentum.CCI;
import it.l_soft.wows.indicators.momentum.CMO;
import it.l_soft.wows.indicators.momentum.CoppockCurve;
import it.l_soft.wows.indicators.momentum.DPO;
import it.l_soft.wows.indicators.momentum.ROC;
import it.l_soft.wows.indicators.momentum.RSI;
import it.l_soft.wows.indicators.momentum.StochasticK;
import it.l_soft.wows.indicators.momentum.TRIX;
import it.l_soft.wows.indicators.momentum.WilliamsR;
import it.l_soft.wows.indicators.trend.Aroon;
import it.l_soft.wows.indicators.trend.EMA;
import it.l_soft.wows.indicators.trend.KAMA;
import it.l_soft.wows.indicators.trend.SMA;
import it.l_soft.wows.indicators.trend.WMA;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.indicators.volatility.MassIndex;
import it.l_soft.wows.indicators.volatility.UlcerIndex;
import it.l_soft.wows.indicators.volume.MFI;

public interface Indicator {
    double NOT_DEFINED = Double.NaN;
    double normalizedValue = 0;
    
    /** Feed one market bar, update internal state. */
    double add(Bar bar);

    /** Current indicator value (may be NOT_DEFINED before ready). */
    double value();

    double getNormalizedValue();
    void setNormalizedValue(double v);
    
    /** Whether the indicator has enough data to produce a defined value. */
    default boolean isReady() { return !Double.isNaN(value()); }

    /** Optional reset. */
    default void reset() {}
    
    default String getName() {
        return this.getClass().getSimpleName();
    }

    default double clamp50(double x) {
        return Math.max(-50.0, Math.min(50.0, x));
    }
    
    default double normalizeAndStore(Bar bar, ATR atrForScaling, ApplicationProperties props) {
        double z = normalize(bar, atrForScaling, props); // your big method
        setNormalizedValue(z);
        return z;
    }

    /**
     * Normalize indicator value to [-50, 50].
     * For some indicators we need the current bar (for price, ATR, band positions).
     * Provide a shared ATR (atrForScaling) already fed each bar to stabilize scaling.
     */
    default double normalize(Bar bar, ATR atrForScaling, ApplicationProperties props) {
        final double v = this.value();
        if (Double.isNaN(v)) return 0.0;

        // BOUNDED oscillators (easy, 0..100 → [-50,50])
        if (this instanceof RSI)                  return v - 50.0;  // RSI in [0,100] → [-50,50]
        if (this instanceof StochasticK ||
            this instanceof StochasticD)          return v - 50.0;  // %K/%D in [0,100] → [-50,50]
        if (this instanceof MFI)                  return v - 50.0;  // MFI in [0,100] → [-50,50]

        // Williams %R in [-100,0] → [-50,50]
        if (this instanceof WilliamsR) {
            return clamp50(v + 50.0);
        }

        // Symmetric oscillators in [-100,100] → [-50,50]
        if (this instanceof CMO ||
            this instanceof Aroon) { // Aroon oscillator is typically in [-100,100]
            return clamp50(v / 2.0);
        }

        if (this instanceof Bollinger) {
            Bollinger b = (Bollinger) this;
            double pB = b.percentB(bar.getClose()); // 0..100
            return Double.isNaN(pB) ? 0.0 : (pB - 50.0);
        }

        if (this instanceof Keltner) {
            Keltner k = (Keltner) this;
            double pct = k.percentKC(bar.getClose()); // 0..100
            return Double.isNaN(pct) ? 0.0 : (pct - 50.0);
        }

        if (this instanceof Donchian) {
            Donchian d = (Donchian) this;
            double upper = d.upper(), lower = d.lower();
            if (Double.isNaN(upper) || Double.isNaN(lower)) return 0.0;
            double range = upper - lower;
            if (range == 0.0) return 0.0;
            double pct = (bar.getClose() - lower) * 100.0 / range; // 0..100
            return clamp50(pct - 50.0);
        }

        // MOMENTUM-like percentages (already percent scale)
        if (this instanceof ROC ||
            this instanceof TRIX ||
            this instanceof CoppockCurve) {
            // Percent-based; just clamp aggressively to [-50,50]
            return clamp50(v);
        }

        // CCI: typical range around [-200,200]; scale to [-50,50]
        if (this instanceof CCI) {
            return clamp50(v / 4.0); // 200/4 = 50
        }

        // MACD (use histogram; scale by ATR%)
        if (this instanceof MACD) {
            MACD m = (MACD) this;
            double hist = m.histogram();
            if (Double.isNaN(hist)) return 0.0;
            double atr = atrForScaling.value();
            if (Double.isNaN(atr) || atr == 0.0) return 0.0;
            // Normalize histogram by ATR, scale to about [-50,50]
            double z = (hist / atr) * 50.0 * props.getMacdToAtrScale();
            return clamp50(z);
        }

        // Awesome Oscillator: also in price units → scale by ATR
        if (this instanceof AwesomeOscillator) {
            double atr = atrForScaling.value();
            if (Double.isNaN(atr) || atr == 0.0) return 0.0;
            double z = (v / atr) * 25.0;
            return clamp50(z);
        }

        // TREND (price vs MA), scale by ATR
        if (this instanceof SMA ||
            this instanceof EMA ||
            this instanceof KAMA ||
            this instanceof WMA) {
            double ma = v;
            double atr = atrForScaling.value();
            if (Double.isNaN(ma) || Double.isNaN(atr) || atr == 0.0) return 0.0;
            double diff = bar.getClose() - ma;
            double z = (diff / atr) * 25.0; // 25 to be conservative vs oscillators
            return clamp50(z);
        }

        // DPO: already "price - SMA shifted" -> treat like MA distance
        if (this instanceof DPO) {
            double atr = atrForScaling.value();
            if (Double.isNaN(atr) || atr == 0.0) return 0.0;
            double z = (v / atr) * 25.0;
            return clamp50(z);
        }

        // Volatility-only indicators

        // ATR: neutral signal (0). You can craft a rule later if needed.
        if (this instanceof ATR) {
            return 0.0;
        }

        // Ulcer Index: drawdown volatility in %; mostly in [0,50] → [0,50] then clamp
        if (this instanceof UlcerIndex) {
            return clamp50(v); // higher ulcer = more risk/upside for mean-reversion
        }

        // Mass Index: typical signal around ~25; center at 25, scale by ~2
        if (this instanceof MassIndex) {
            double z = (v - 25.0) * 2.0;
            return clamp50(z);
        }

        // OBV & other cumulative / exotic stuff:
        // Fallback: treat as z-ish using ATR as scale of typical move (if meaningful)
        double atr = atrForScaling.value();
        if (!Double.isNaN(atr) && atr != 0.0) {
            return clamp50((v / atr) * 10.0);
        }
        return 0.0;
    }
}
