package it.l_soft.wows.evaluator;

import java.util.ArrayList;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.ga.GeneInterface;
import it.l_soft.wows.indicators.IndicatorContext;
import it.l_soft.wows.indicators.volatility.ATR;

public interface FitnessEvaluator {

    /**
     * Evaluate the fitness of a gene over a given series.
     */
    double evaluate(GeneInterface gene,
                    ArrayList<Bar>series,
                    ATR atr,
                    IndicatorContext ctx,
                    TargetBuilder targetBuilder,
                    PredictionToPriceMapper mapper,
                    ApplicationProperties props);
}
