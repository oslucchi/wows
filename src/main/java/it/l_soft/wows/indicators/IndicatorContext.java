package it.l_soft.wows.indicators;

public interface IndicatorContext {

    // How many time steps we have
    int length();

    // Raw price info
    double getClose(int t);

    // Raw ATR (streaming indicator you recorded)
    double getAtr(int t);

    // Close at horizon (for target computation)
    double getCloseAt(int tPlusH);

    // Normalized indicator value for indicator j at time t
    double getNormalizedIndicator(int indicatorIndex, int t);
}
