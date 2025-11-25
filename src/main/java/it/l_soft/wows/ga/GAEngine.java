package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.ga.Gene.ScoreHolder;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.TextFileHandler;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

//import org.apache.log4j.Logger;

public final class GAEngine {
//	private final Logger log = Logger.getLogger(this.getClass());
	ApplicationProperties props = ApplicationProperties.getInstance();
    private final List<Indicator> catalog; // all available indicator *prototypes* (NOT shared state!)
    @SuppressWarnings("unchecked")
    private final List<Gene>[] populations = (List<Gene>[]) new ArrayList[props.getNumberOfPopulations()];
    
    private TextFileHandler outGeneEvolution;
    private Gene[] arbitrators = null;
    @SuppressWarnings("unchecked")
	private List<Gene>[] ranks = (List<Gene>[]) new List[props.getNumberOfPopulations()];;

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
    }

    private void initPopulation() {
    	arbitrators = new Gene[props.getNumberOfPopulations()];
    	for (int y = 0; y < props.getNumberOfPopulations(); y++)
    	{
    		populations[y] = new ArrayList<Gene>();
            populations[y].clear();
            for (int i = 0; i < props.getPopulationSize(y); i++) {
                populations[y].add(randomGene(y));
            }
            arbitrators[y] = null;
            try {
            	TextFileHandler temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
            											   props.getGeneEvalDumpName() + "_genes_" + y, 
            											   "csv", false, false);
        		temp.write("Gene,Indicators,BarsSurvived,MktTS,MktBar#,MktDir,Direction (M-P)," +
     				   	   "PrevClose,CurrClose,PredMktPrice,PredMove,PredMovSign,Denom,NextPredScore", true);
    			temp.close();
            	temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
    									   props.getGeneEvalDumpName() + "_arbi_" + y, 
    									   "csv", false, false);
    			temp.write("Gene,Indicators,BarsSurvived,MktTS,MktBar#,MktDir,Direction (M-P)," +
    						"PrevClose,CurrClose,PredMktPrice,PredMove,Denom,NextPredScore", true);
    			temp.close();
    		} catch (Exception e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	System.out.println("QUI");
    }

    private Gene randomGene(int populationIdx) {
    	
    	String geneIndicators = "";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] indIdx = new int[props.getGeneSize(populationIdx)];
        double[] weigths = new double[props.getGeneSize(populationIdx)];
        
        for (int i = 0; i < props.getGeneSize(populationIdx); i++) {
        	// use the n-th indicator in the indicators list
        	// theoretically an indicator could appear more than once in a gene
        	indIdx[i] = r.nextInt(catalog.size());
        	weigths[i] = 1;
        	geneIndicators += catalog.get(indIdx[i]).getName() + " ";
        }
        Gene g = new Gene(geneIndicators, indIdx, weigths, populationIdx);
        return g;
    }
    
    public void evalPopulations(List<Indicator> indicators, MarketBar currBar, 
			   					MarketBar prevBar, double denom)
    {
    	for(int populationIdx = 0; populationIdx < props.getNumberOfPopulations(); populationIdx++)
    	{
    		evalPopulation(populationIdx, indicators, currBar, prevBar, denom);
    	}
    }
    
    public void evalPopulation(int populationIdx, List<Indicator> indicators, MarketBar currBar, 
    						   MarketBar prevBar, double denom)
    {
    	// do the evaluation math on each gene
    	int i = 0;
    	for (Gene g : populations[populationIdx]) 
        {
        	g.geneEvalMaths(g, indicators, currBar, prevBar, denom, "gene" + i++);
        }
    	
    	// do the math on the predictor
    	if (arbitrators[populationIdx] != null)
    	{
    		arbitrators[populationIdx].geneEvalMaths(arbitrators[populationIdx], indicators, 
    															 currBar, prevBar, 
    															 denom, "arbitrator");
    	}
    	else
    	{
    		arbitrators[populationIdx] = new Gene("arbitrator", null, null, populationIdx);
    	}

    	// rank the population after evaluating their score
    	ranks[populationIdx] = ranked(populations[populationIdx]);

    	// Set the new prediction for predictor
    	Gene firstInRank = ranks[populationIdx].getFirst(); 
    	ScoreHolder firstInRankScore;
    	@SuppressWarnings("unchecked")
		List<ScoreHolder> scoresAsList = firstInRank.getReader().getContentAsList();
    	if ((scoresAsList != null) && (scoresAsList.size() > 0))
    	{
    		firstInRankScore = scoresAsList.getLast();
    	}
    	else
    	{
    		firstInRankScore = arbitrators[populationIdx].new ScoreHolder();
    		firstInRankScore.timestamp = 0;
    		firstInRankScore.direction = 0;
    		firstInRankScore.predictedMarketPrice = 0;
    	}
        
        ScoreHolder newPrediction = arbitrators[populationIdx].new ScoreHolder();
        
        arbitrators[populationIdx].setName(firstInRank.getName());
        arbitrators[populationIdx].setIndicatorIndices(firstInRank.getIndicatorIndices());
        newPrediction.name = arbitrators[populationIdx].getName();
        newPrediction.timestamp = firstInRankScore.timestamp;
        newPrediction.direction = firstInRankScore.direction;
        newPrediction.predictedMarketPrice = firstInRankScore.predictedMarketPrice;
        arbitrators[populationIdx].getScores().publish(newPrediction);
    }
    
    public List<Gene> ranked(List<Gene> population) {
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

    public void evolve()
    {
    	
    	for (int populationIdx = 0; populationIdx < populations.length; populationIdx++)
    	{
    		evolvePopulation(populationIdx);
    	}
    }

    /** Selection: top keep, middle crossover, bottom replaced. */
    public void evolvePopulation(int populationIdx) {
    	
        int n = ranks[populationIdx].size();
        int keep = (int) Math.round(n * props.getElitePct());
        int cross= (int) Math.round(n * props.getCrossoverPct());

        List<Gene> next = new ArrayList<Gene>(n);

        // 1) Elites
        for (int i = 0; i < keep; i++) {
            next.add(ranks[populationIdx].get(i));
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 2) Crossover (produce 'crossed' children)
        for (int i = keep; i < keep + cross; i += 2) {
        	if (i + 1 > populations[populationIdx].size()) break;
        	Gene a = populations[populationIdx].get(i);
        	Gene b = populations[populationIdx].get(i + 1);
        	crossover(populationIdx, i, rnd);
            next.add(a);
            next.add(b);
        }

        // 3) Replacement (random new)
        while (next.size() < n) {
            next.add(randomGene(populationIdx));
        }

        populations[populationIdx].clear();
        populations[populationIdx].addAll(next);
        // Reset horizon buffer so we don’t mix pre/post evolution windows
        atrScale.reset();
        try {
        	String sep = "";
	        for(Gene g : populations[populationIdx])
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

    private void crossover(int populationIdx, int geneAIdx, ThreadLocalRandom rnd) {
    	int geneSize = props.getGeneSize(populationIdx);
    	
    	Gene a = populations[populationIdx].get(geneAIdx);
        Gene b = populations[populationIdx].get(geneAIdx + 1);
    	for (int i = 0; i < geneSize; i++) {
            if (rnd.nextInt(2) == 1)
            {
            	int k = a.getIndicatorIndices()[i];
            	a.getIndicatorIndices()[i] = b.getIndicatorIndices()[i];
            	b.getIndicatorIndices()[i] = k;
            }
        }
        String geneNameA = "";
        String geneNameB = "";
        for (int i = 0; i < geneSize; i++) {
            geneNameA += catalog.get(a.getIndicatorIndices()[i]).getName() + " ";
            geneNameB += catalog.get(b.getIndicatorIndices()[i]).getName() + " ";
        }
        a.setName(geneNameA);
        a.resetIndicators();
        b.setName(geneNameB);
        b.resetIndicators();
    }    
    
    public List<Gene> getPopulation(int i) {
    	return populations[i];
    }
    
    public void cleanUpOnExit() {
    	outGeneEvolution.close();
    }

	public List<Gene>getRank(int i) {
		return ranks[i];
	}
	
	public Gene getArbitrator(int i) {
		return arbitrators[i];
	}
}
