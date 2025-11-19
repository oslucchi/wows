package it.l_soft.wows.indicators;

import java.util.List;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.volatility.ATR;

public final class IndicatorContextBuilder {

    private final List<Bar> history;
    private final List<Indicator> indicators; // your wrapper type
    private final ATR atrIndicator;      // or one of the above
    private final ApplicationProperties props;

    public IndicatorContextBuilder(List<Bar> history,
                                   List<Indicator> indicators,
                                   ATR atrIndicator,
                                   ApplicationProperties props) {
        this.history = history;
        this.indicators = indicators;
        this.atrIndicator = atrIndicator;
        this.props = props;
    }

    public ArrayIndicatorContext build() {
        int length = history.size();
        int numIndicators = indicators.size();

        ArrayIndicatorContext ctx = new ArrayIndicatorContext(length, numIndicators);

        // Stream through bars in chronological order
        for (int t = 0; t < length; t++) {
            Bar bar = history.get(t);

            // 1) update all indicators with the new bar (streaming mode)
            atrIndicator.add(bar); // however you do it
            for (Indicator ind : indicators) {
                ind.add(bar);
            }

            // 2) store close and ATR for this time step
            double close = bar.getClose();
            double atr = atrIndicator.value();
            ctx.setClose(t, close);
            ctx.setAtr(t, atr);

            // 3) store normalized value for each indicator at this time step
            for (int i = 0; i < numIndicators; i++) {
            	Indicator ind = indicators.get(i);
                double norm = ind.normalize(bar, atrIndicator, props);
                ctx.setNormalizedIndicator(i, t, norm);
            }
        }

        return ctx;
    }
}