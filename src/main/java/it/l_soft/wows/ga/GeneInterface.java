package it.l_soft.wows.ga;

import it.l_soft.wows.indicators.IndicatorContext;

public interface GeneInterface {
    double computeSignal(int t, IndicatorContext ctx);
}
