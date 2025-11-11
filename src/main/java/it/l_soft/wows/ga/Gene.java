package it.l_soft.wows.ga;

import it.l_soft.wows.indicators.Indicator;

import java.util.Arrays;

public final class Gene {
    public final Indicator[] indicators; // chosen by index into your master list
    public int barsSeen = 0;

    // online score accumulator
    public int score = 0;

    public Gene(Indicator[] indicators) {
        this.indicators = indicators;
    }

    public Gene copyShallow() {
        return new Gene(Arrays.copyOf(indicators, indicators.length));
    }
}
