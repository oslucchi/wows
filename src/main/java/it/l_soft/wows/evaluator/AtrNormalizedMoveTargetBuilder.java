package it.l_soft.wows.evaluator;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.IndicatorContext;

public final class AtrNormalizedMoveTargetBuilder implements TargetBuilder {

    @Override
    public double buildTarget(int t, IndicatorContext ctx, ApplicationProperties props) {
        int H = props.getHorizonBars();
        double K = props.getAtrNormScale();

        int futureIndex = t + H;
        if (futureIndex >= ctx.length()) {
            return Double.NaN; // no future bar available
        }

        double closeNow    = ctx.getClose(t);
        double closeFuture = ctx.getCloseAt(futureIndex);
        double atrNow      = ctx.getAtr(t);

        if (atrNow == 0.0 || Double.isNaN(atrNow)) {
            return 0.0;
        }

        double rawMove    = closeFuture - closeNow;   // ΔP
        double atrMoves   = rawMove / atrNow;         // in ATR units
        double normalized = atrMoves * K;             // scale to [-50,50]-ish

        return clamp50(normalized);
    }

    private static double clamp50(double x) {
        if (x >  50.0) return  50.0;
        if (x < -50.0) return -50.0;
        return x;
    }
}
