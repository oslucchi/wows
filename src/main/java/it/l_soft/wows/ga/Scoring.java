package it.l_soft.wows.ga;

final class Scoring {
    private Scoring() {}

    /** Combine indicators: sum normalized signals in [-50,50], then threshold to decision. */
    static Decision decide(double sumSignal) {
        if (sumSignal >  5.0) return Decision.LONG;   // small deadband around zero
        if (sumSignal < -5.0) return Decision.SHORT;
        return Decision.HOLD;
    }

    /** Compare next move vs decision: +1 correct, -1 wrong, 0 for HOLD inside threshold. */
    static int score(Decision d, double pctChange, double holdThresholdPct) {
        switch (d) {
            case LONG:
                return pctChange >  holdThresholdPct ? +1 : -1;
            case SHORT:
                return pctChange < -holdThresholdPct ? +1 : -1;
            default: // HOLD
                return Math.abs(pctChange) <= holdThresholdPct ? +1 : -1;
        }
    }
}
