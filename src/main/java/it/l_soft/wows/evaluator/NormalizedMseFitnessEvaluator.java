package it.l_soft.wows.evaluator;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.ga.GeneInterface;
import it.l_soft.wows.indicators.IndicatorContext;

public final class NormalizedMseFitnessEvaluator {

    public double evaluate(GeneInterface gene,
                           IndicatorContext ctx,
                           TargetBuilder targetBuilder,
                           ApplicationProperties props) {

        int end = ctx.length() - props.getHorizonBars();
        double sumSq = 0.0;
        int count = 0;

        for (int t = 0; t < end; t++) {
            double target = targetBuilder.buildTarget(t, ctx, props);
            if (Double.isNaN(target)) continue;

            double signal = gene.computeSignal(t, ctx);
            double err = signal - target;

            sumSq += err * err;
            count++;
        }

        if (count == 0) return 0.0;
        double mse = sumSq / count;

        return 1.0 / (1.0 + mse); // higher is better
    }
}
