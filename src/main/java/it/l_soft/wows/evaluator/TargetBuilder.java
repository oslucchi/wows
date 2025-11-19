package it.l_soft.wows.evaluator;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.IndicatorContext;

public interface TargetBuilder {
    double buildTarget(int t, IndicatorContext ctx, ApplicationProperties props);
}