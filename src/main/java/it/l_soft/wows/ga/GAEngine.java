package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.ga.Gene.ScoreHolder;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.TextFileHandler;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.log4j.Logger;

public final class GAEngine {
	private final Logger log = Logger.getLogger(this.getClass());
	ApplicationProperties props = ApplicationProperties.getInstance();
    private final List<Indicator> catalog; // all available indicator *prototypes* (NOT shared state!)
    private final List<Gene> population = new ArrayList<>();
    private TextFileHandler outGeneEvolution;
    private Gene predictor = null;
    private List<Gene> rank;

    // Shared ATR for normalization scaling
    private final ATR atrScale;

    // For horizon scoring, keep a ring of past closes (or compute pct when horizon fulfilled)
    private final Deque<Double> closeRing = new ArrayDeque<>();

    public GAEngine(List<Indicator> indicatorCatalog) {
        this.catalog = indicatorCatalog;
        this.atrScale = new ATR(props.getAtrPeriodForScaling());
        try {
			this.outGeneEvolution = new TextFileHandler(props.getGeneEvolutionFilePath(), "GE", "txt");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			this.outGeneEvolution = null;
			e.printStackTrace();
		}
        initPopulation();
    }

    private void initPopulation() {
        population.clear();
        for (int i = 0; i < props.getPopulationSize(); i++) {
            population.add(randomGene());
        }
    }

    private Gene randomGene() {
    	String geneIndicators = "";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] indIdx = new int[props.getGeneSize()];
        double[] weigths = new double[props.getGeneSize()];
        
        for (int i = 0; i < props.getGeneSize(); i++) {
        	// use the n-th indicator in the indicators list
        	// theoretically an indicator could appear more than once in a gene
        	indIdx[i] = r.nextInt(catalog.size());
        	weigths[i] = 1;
        	geneIndicators += catalog.get(indIdx[i]).getName() + " ";
        }
        Gene g = new Gene(geneIndicators, indIdx, weigths);
//        log.debug("Indicators selected for the current gene are: " + g.getName());
        return g;
    }
 
/*
    private boolean shouldEvolveNow() {
        // Example trigger: every 250 bars *and* every gene saw enough bars
        // You can wire a timer/iteration counter; here keep it simple:
        return population.stream().allMatch(g -> g.warmedUp());
    }
*/
    private void geneEvalMaths(Gene g, List<Indicator> indicators, MarketBar currBar, 
			   MarketBar prevBar, double denom)
    {
        double z = 0.0;
        for (int i : g.getIndicatorIndices()) {
            // each indicator normalized to [-50,50] → divide by 50 → [-1,1]
            z += indicators.get(i).getNormalizedValue() / 50.0;
        }
        z /= Math.max(1, g.getIndicatorIndices().length); // average
        double yhat = Math.tanh(z / props.getPredictionTemperature());
		try {
			g.evaluateScorePrediction(currBar, 
									  (int)(Math.signum(currBar.getClose() - prevBar.getClose())),
									  yhat,
									  denom);
		} catch (MissedItemsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        log.trace(String.format("[BAR %d][GENE %s] z=%.4f yhat=%.4f", 
				currBar.getBarNumber(), g.getName(), z, yhat));
    }
    
    public void evalPopulation(List<Indicator> indicators, MarketBar currBar, 
    						   MarketBar prevBar, double denom)
    {
    	// do the evaluation math on each gene
    	for (Gene g : population) 
        {
        	geneEvalMaths(g, indicators, currBar, prevBar, denom);
        }
    	
    	// do the math on the predictor
    	if (predictor != null)
    	{
    		geneEvalMaths(predictor, indicators, currBar, prevBar, denom);
    	}
    	else
    	{
    		predictor = new Gene("", null, null);
    	}

    	// rank the population after evaluating their score
    	rank = ranked();

    	// Set the new prediction for predictor
    	Gene firstInRank = rank.getFirst(); 
    	ScoreHolder firstInRankScore;
    	@SuppressWarnings("unchecked")
		List<ScoreHolder> scoresAsList = firstInRank.getReader().getContentAsList();
    	if ((scoresAsList != null) && (scoresAsList.size() > 0))
    	{
    		firstInRankScore = scoresAsList.getLast();
    	}
    	else
    	{
    		firstInRankScore = predictor.new ScoreHolder();
    		firstInRankScore.timestamp = 0;
    		firstInRankScore.direction = 0;
    		firstInRankScore.predictedMarketPrice = 0;
    	}
        
        ScoreHolder newPrediction = predictor.new ScoreHolder();
        
        predictor.setName(firstInRank.getName());
        predictor.setIndicatorIndices(firstInRank.getIndicatorIndices());
        newPrediction.name = predictor.getName();
        newPrediction.timestamp = firstInRankScore.timestamp;
        newPrediction.direction = firstInRankScore.direction;
        newPrediction.predictedMarketPrice = firstInRankScore.predictedMarketPrice;
        predictor.getScores().publish(newPrediction);
    }
    
    public List<Gene> ranked() {
        final double TOTAL_WEIGHT = props.getWaitOfScoreInRanking();
        final double WINRATE_WEIGHT = props.getWaitOfWinRateInRanking();
        return population.stream()
                .sorted(Comparator.comparingDouble((Gene g) -> {
                    double totalScore = g.getTotalScore();
                    double winRate = g.getWinRate(); // 0–1 range
                    return totalScore * TOTAL_WEIGHT + winRate * WINRATE_WEIGHT;
                }).reversed())
                .toList();
    }


    /** Selection: top keep, middle crossover, bottom replaced. */
    public void evolve() {
        int n = rank.size();
        int keep = (int) Math.round(n * props.getElitePct());
        int cross= (int) Math.round(n * props.getCrossoverPct());

        List<Gene> next = new ArrayList<Gene>(n);

        // 1) Elites
        for (int i = 0; i < keep; i++) {
            next.add(rank.get(i));
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 2) Crossover (produce 'cross' children)
        while (next.size() < keep + cross) {
        	Gene p1 = tournament(rank, 4, rnd);
        	Gene p2 = tournament(rank, 4, rnd);
        	Gene child = crossover(p1, p2, rnd);
            mutate(child, rnd);
            resetIndicators(child);
            next.add(child);
        }

        // 3) Replacement (random new)
        while (next.size() < n) {
            next.add(randomGene());
        }

        population.clear();
        population.addAll(next);
        // Reset horizon buffer so we don’t mix pre/post evolution windows
        closeRing.clear();
        atrScale.reset();
        try {
        	String sep = "";
	        for(Gene g : population)
	        {
		        outGeneEvolution.write(sep + g.getName(), false);
		        sep = ",";
	        }
	        outGeneEvolution.write("", true);
        }
        catch(Exception e)
        {
        	;
        }
    }

    private Gene tournament(List<Gene> rank, int k, ThreadLocalRandom rnd) {
    	Gene best = null;
        for (int i = 0; i < k; i++) {
        	Gene g = rank.get(rnd.nextInt(rank.size()));
            if (best == null || g.getTotalScore() > best.getTotalScore()) best = g;
        }
        return best;
    }

    private Gene crossover(Gene a, Gene b, ThreadLocalRandom rnd) {
        int[] child = new int[props.getGeneSize()];
        double[] weigths = new double[props.getGeneSize()];
        int cut = rnd.nextInt(props.getGeneSize()); // single-point
        String geneName = "";
        for (int i = 0; i < props.getGeneSize(); i++) {
            int proto = (i < cut ? a.getIndicatorIndices()[i] : b.getIndicatorIndices()[i]);
            child[i] = proto;
            geneName += catalog.get(proto).getName() + " ";
        }
        return new Gene(geneName, child, weigths);
    }

    private void mutate(Gene g, ThreadLocalRandom rnd) {
        if (rnd.nextDouble() > props.getMutationRate()) return;
        ;
    }

    private void resetIndicators(Gene g) {
    	;
    }
    
    
    public List<Gene> getPopulation() {
    	return population;
    }
    
    public void cleanUpOnExit() {
    	outGeneEvolution.close();
    }

	public List<Gene>getRank() {
		return rank;
	}
	
	public Gene getPredictor() {
		return predictor;
	}
}
