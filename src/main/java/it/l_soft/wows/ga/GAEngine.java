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
    private Gene arbitrator = null;
    private List<Gene> rank;

    // Shared ATR for normalization scaling
    private final ATR atrScale;

    // For horizon scoring, keep a ring of past closes (or compute pct when horizon fulfilled)
//  private final Deque<Double> closeRing = new ArrayDeque<>();

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
        try {
        	TextFileHandler temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
        											   props.getGeneEvalDumpName() + "_" + "gene", 
        											   "csv", false, false);
    		temp.write("Gene,Indicators,BarsSurvived,MktTS,MktBar#,MktDir,Direction (M-P)," +
 				   	   "PrevClose,CurrClose,PredMktPrice,PredMove,PredMovSign,Denom,NextPredScore", true);
			temp.close();
        	temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
									   props.getGeneEvalDumpName() + "_" + "arbi", 
									   "csv", false, false);
			temp.write("Gene,Indicators,BarsSurvived,MktTS,MktBar#,MktDir,Direction (M-P)," +
						"PrevClose,CurrClose,PredMktPrice,PredMove,Denom,NextPredScore", true);
			temp.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
        return g;
    }
 
    private void geneEvalMaths(Gene g, List<Indicator> indicators, MarketBar currBar, 
			   MarketBar prevBar, double denom, String name)
    {
        double z = 0.0;
        for (int i : g.getIndicatorIndices()) {
            // each indicator normalized to [-50,50] → divide by 50 → [-1,1]
            z += indicators.get(i).getNormalizedValue() / 50.0;
        }
        z /= Math.max(1, g.getIndicatorIndices().length); // average
        double yhat = Math.tanh(z / props.getPredictionTemperature());
		try {
			g.evaluateScorePrediction(currBar, prevBar,
									  (int)(Math.signum(currBar.getClose() - prevBar.getClose())),
									  yhat,
									  denom, 
									  name);
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
    	int i = 0;
    	for (Gene g : population) 
        {
        	geneEvalMaths(g, indicators, currBar, prevBar, denom, "gene" + i++);
        }
    	
    	// do the math on the predictor
    	if (arbitrator != null)
    	{
    		geneEvalMaths(arbitrator, indicators, currBar, prevBar, denom, "arbitrator");
    	}
    	else
    	{
    		arbitrator = new Gene("arbitrator", null, null);
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
    		firstInRankScore = arbitrator.new ScoreHolder();
    		firstInRankScore.timestamp = 0;
    		firstInRankScore.direction = 0;
    		firstInRankScore.predictedMarketPrice = 0;
    	}
        
        ScoreHolder newPrediction = arbitrator.new ScoreHolder();
        
        arbitrator.setName(firstInRank.getName());
        arbitrator.setIndicatorIndices(firstInRank.getIndicatorIndices());
        newPrediction.name = arbitrator.getName();
        newPrediction.timestamp = firstInRankScore.timestamp;
        newPrediction.direction = firstInRankScore.direction;
        newPrediction.predictedMarketPrice = firstInRankScore.predictedMarketPrice;
        arbitrator.getScores().publish(newPrediction);
    }
    
    public List<Gene> ranked() {
        final double TOTAL_WEIGHT = props.getWaitOfScoreInRanking();
        final double WINRATE_WEIGHT = props.getWaitOfWinRateInRanking();
        return population.stream()
                .sorted(Comparator.comparingDouble((Gene g) -> {
                    double totalScore = g.getTotalScore() / g.getTotalBarsSurviving();
                    double winRate = g.getWinRate() / g.getTotalBarsSurviving();
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

        // 2) Crossover (produce 'crossed' children)
        for (int i = keep; i < keep + cross; i += 2) {
        	if (i + 1 > population.size()) break;
        	Gene a = population.get(i);
        	Gene b = population.get(i + 1);
        	crossover(i, rnd);
            next.add(a);
            next.add(b);
        }

        // 3) Replacement (random new)
        while (next.size() < n) {
            next.add(randomGene());
        }

        population.clear();
        population.addAll(next);
        // Reset horizon buffer so we don’t mix pre/post evolution windows
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

    private void crossover(int geneAIdx, ThreadLocalRandom rnd) {
    	Gene a = population.get(geneAIdx);
        Gene b = population.get(geneAIdx + 1);
    	for (int i = 0; i < props.getGeneSize(); i++) {
            if (rnd.nextInt(2) == 1)
            {
            	int k = a.getIndicatorIndices()[i];
            	a.getIndicatorIndices()[i] = b.getIndicatorIndices()[i];
            	b.getIndicatorIndices()[i] = k;
            }
        }
        String geneNameA = "";
        String geneNameB = "";
        for (int i = 0; i < props.getGeneSize(); i++) {
            geneNameA += catalog.get(a.getIndicatorIndices()[i]).getName() + " ";
            geneNameB += catalog.get(b.getIndicatorIndices()[i]).getName() + " ";
        }
        a.setName(geneNameA);
        a.resetIndicators();
        b.setName(geneNameB);
        b.resetIndicators();
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
	
	public Gene getArbitrator() {
		return arbitrator;
	}
}
