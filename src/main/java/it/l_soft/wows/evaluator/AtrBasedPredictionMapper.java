package it.l_soft.wows.evaluator;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.IndicatorContext;

public final class AtrBasedPredictionMapper implements PredictionToPriceMapper {

    @Override
    public double predictFutureClose(int t,
                                     double signal,
                                     IndicatorContext ctx,
                                     ApplicationProperties props) {
        double K = props.getAtrNormScale();

        double closeNow = ctx.getClose(t);
        double atrNow   = ctx.getAtr(t);

        if (atrNow == 0.0 || Double.isNaN(atrNow)) {
            return closeNow;
        }

        double atrMoves  = signal / K;       // predicted ATR-multiples
        double priceDiff = atrMoves * atrNow;
        return closeNow + priceDiff;
    }
}
