package it.l_soft.wows.ga;

import it.l_soft.wows.comms.Price;

public final class GAConfig {
    public int geneSize = 5;                  // indicators per gene
    public int populationSize = 200;

    // Scoring horizon + HOLD threshold
    public int horizonBars = 1;               // predict next bar’s direction over this horizon
    public double holdThresholdPct = 0.1;     // abs % change <= this ⇒ HOLD

    // Selection percentages
    public double elitePct = 0.25;            // survive unchanged
    public double crossoverPct = 0.50;        // produced by crossover
    public double replacePct = 0.25;          // random new

    // Mutation
    public double mutationRate = 0.08;        // per child
    public double mutationSwapRate = 0.30;    // chance to swap two loci in a gene

    // Evaluation gating
    public int  minBarsBeforeScoring = 50;    // gene must see at least this many bars
    public boolean earlyCullAtEliteFloor = true;

    // Normalization helpers
    public int atrPeriodForScaling = 14;      // used to scale unbounded signals
    public double macdToAtrScale = 1.0;       // scale factor for MACD hist vs ATR
    public Price defaultPrice = Price.CLOSE;  // for trend comparators, etc.
}
