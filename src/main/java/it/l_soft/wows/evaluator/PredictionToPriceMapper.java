package it.l_soft.wows.evaluator;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.IndicatorContext;

public interface PredictionToPriceMapper {
    double predictFutureClose(int t, double signal, IndicatorContext ctx, ApplicationProperties props);
}
