package it.l_soft.wows.indicators;

import it.l_soft.wows.comms.Bar;

public interface Indicator {
    double NOT_DEFINED = Double.NaN;

    /** Feed one market bar, update internal state. */
    double add(Bar bar);

    /** Current indicator value (may be NOT_DEFINED before ready). */
    double value();

    /** Whether the indicator has enough data to produce a defined value. */
    default boolean isReady() { return !Double.isNaN(value()); }

    /** Optional reset. */
    default void reset() {}
}
