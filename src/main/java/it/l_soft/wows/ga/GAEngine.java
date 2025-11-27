package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.ga.Gene.ScoreHolder;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;
import it.l_soft.wows.utils.TextFileHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.log4j.Logger;

public class GAEngine {
	private final Logger log = Logger.getLogger(this.getClass());
    public static final int TOTAL_RECORDS = 0;
    public static final int MATCHES = 1;
    public static final int FLAT = 2;
    public static final int ERRORS = 3;	
    public static final int TOTAL_ACCUMULATORS = 4;
    
	ApplicationProperties props = ApplicationProperties.getInstance();
    private final List<Indicator> catalog; // all available indicator *prototypes* (NOT shared state!)
    private final PopulationInstance[] population = new PopulationInstance[props.getNumberOfPopulations()];
    
    @SuppressWarnings("unchecked")
	private List<Gene>[] ranks = (List<Gene>[]) new List[props.getNumberOfPopulations()];;

    // Shared ATR for normalization scaling
    private final ATR atrScale;

	// For horizon scoring, keep a ring of past closes (or compute pct when horizon fulfilled)
	//  private final Deque<Double> closeRing = new ArrayDeque<>();

    public GAEngine(List<Indicator> indicatorCatalog) {
        this.catalog = indicatorCatalog;
        this.atrScale = new ATR(props.getAtrPeriodForScaling());
        for(int i = 0; i < props.getNumberOfPopulations(); i++)
        {
            List<Gene> genes = new ArrayList<Gene>();
            for (int y = 0; y < props.getPopulationSize(i); y++) {
                genes.add(randomGene(props.getPopulationSize(i)));
            }
        	population[i] = PopulationInstance.initPopulation(i, genes, props.getHorizonBars(i), TOTAL_ACCUMULATORS);
        }
    }

    private Gene randomGene(int populationSize) {
    	
    	String geneIndicators = "";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] indIdx = new int[populationSize];
        double[] weigths = new double[populationSize];
        
        for (int i = 0; i < populationSize; i++) {
        	// use the n-th indicator in the indicators list
        	// theoretically an indicator could appear more than once in a gene
        	indIdx[i] = r.nextInt(catalog.size());
        	weigths[i] = 1;
        	geneIndicators += catalog.get(indIdx[i]).getName() + " ";
        }
        Gene g = new Gene(geneIndicators, indIdx, weigths);
        return g;
    }
    
    public void evalPopulations(List<Indicator> indicators, MarketBar currBar, 
			   					MarketBar prevBar, double denom) throws MissedItemsException
    {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.sss");

    	double consolidatedPrediction= 0;
    	StringBuilder sb = new StringBuilder();
		sb.append(String.format("%d,%s,",
								prevBar.getBarNumber(),
								sdf.format(new Date(prevBar.getTimestamp()))));
    	for(int i = 0; i < props.getNumberOfPopulations(); i++)
    	{
    		population[i] = evalPopulation(population[i], indicators, currBar, prevBar, denom);
    		try {
    			if (prevBar.getBarNumber() > 50)
    			{
    				consolidatedPrediction += population[i].arbitrator.getPrediction(prevBar.getBarNumber()).direction;
        			sb.append(String.format("%d,", 
        					population[i].arbitrator.getPrediction(prevBar.getBarNumber()).direction));
    			}
    			else
    			{
        			sb.append(String.format("0,"));     				
    			}
			} catch (MissedItemsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}    		
    	}
		sb.append(String.format("%.4f,", consolidatedPrediction));
		TextFileHandler temp;
		try {
			temp = new TextFileHandler(props.getGeneEvalDumpPath(),"SuperArbitrator", "csv", false, true);
			temp.write(sb.toString(), true);
			temp.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public PopulationInstance evalPopulation(PopulationInstance populationInstance, 
    										 List<Indicator> indicators, MarketBar currBar, 
    										 MarketBar prevBar, double denom) 
    		throws MissedItemsException
    {
    	// do the evaluation math on each gene
    	double yhat;

		GeneEvaluator ge;
    	for (Gene g : populationInstance.genes) 
        {
    		ge = new GeneEvaluator(g, indicators, populationInstance);
    		yhat = ge.predictMarketMovement();

    		ge.evaluateScorePrediction(currBar, prevBar, 
        							   yhat, denom, 
        							   g.getName());
        }
    	
    	Gene arbitrator = populationInstance.arbitrator;
    	// do the math on the predictor
    	if (arbitrator != null)
    	{
    		ge = new GeneEvaluator(arbitrator, indicators, populationInstance);
    		yhat = ge.predictMarketMovement();

    		ge.evaluateScorePrediction(currBar, prevBar, 
        							   yhat, denom, 
        							   "arbitrator");
    	}
    	else
    	{
    		populationInstance.arbitrator = arbitrator = new Gene("arbitrator", new int[0], new double[0]); 
    	}
    	

    	// rank the population after evaluating their score
    	populationInstance.roundRank = ranked(populationInstance.genes);

    	// Set the new prediction for predictor
    	Gene firstInRank = populationInstance.roundRank.getFirst(); 
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
        
        // arbitrator.setName(firstInRank.getName());
        arbitrator.setIndicatorIndices(firstInRank.getIndicatorIndices());
        newPrediction.name = arbitrator.getName();
        newPrediction.timestamp = firstInRankScore.timestamp;
        newPrediction.direction = firstInRankScore.direction;
        newPrediction.predictedMarketPrice = firstInRankScore.predictedMarketPrice;
//        arbitrator.setTotalBarsSurviving(arbitrator.getTotalBarsSurviving() + 1);
        
        arbitrator.getScores().publish(newPrediction);
        
        return populationInstance;
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
    	for (int i = 0; i < props.getNumberOfPopulations(); i++)
    	{
    		evolvePopulation(population[i]);
    	}
    }

    /** Selection: top keep, middle crossover, bottom replaced. */
    public PopulationInstance evolvePopulation(PopulationInstance populationInstance) {
    	
        int n = populationInstance.size;
        int keep = (int) Math.round(n * props.getElitePct());
        int cross= (int) Math.round(n * props.getCrossoverPct());

        List<Gene> next = new ArrayList<Gene>(n);

        // 1) Elites
        for (int i = 0; i < keep; i++) {
            next.add(populationInstance.genes.get(i));
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 2) Crossover (produce 'crossed' children)
        for (int i = keep; i < keep + cross; i += 2) {
        	if (i + 1 > populationInstance.size) break;
        	Gene[] crossed = crossover(populationInstance, i, rnd);
            next.add(crossed[0]);
            next.add(crossed[1]);
        }

        // 3) Replacement (random new)
        while (next.size() < n) {
            next.add(randomGene(populationInstance.size));
        }

        populationInstance.genes.clear();
        populationInstance.genes.addAll(next);
        // Reset horizon buffer so we don’t mix pre/post evolution windows
        atrScale.reset();
        
        return populationInstance;
    }

    private Gene[] crossover(PopulationInstance populationInstance, int geneAIdx, ThreadLocalRandom rnd) {
    	int geneSize = populationInstance.size;
    	
    	Gene[] crossed = new Gene[2];
    	crossed[0] = populationInstance.genes.get(geneAIdx);
        crossed[1] = populationInstance.genes.get(geneAIdx + 1);
    	for (int i = 0; i < geneSize; i++) {
            if (rnd.nextInt(2) == 1)
            {
            	int k = crossed[0].getIndicatorIndices()[i];
            	crossed[0].getIndicatorIndices()[i] = crossed[1].getIndicatorIndices()[i];
            	crossed[1].getIndicatorIndices()[i] = k;
            }
        }
        String geneNameA = "";
        String geneNameB = "";
        for (int i = 0; i < geneSize; i++) {
            geneNameA += catalog.get(crossed[0].getIndicatorIndices()[i]).getName() + " ";
            geneNameB += catalog.get(crossed[1].getIndicatorIndices()[i]).getName() + " ";
        }
        crossed[0].setName(geneNameA);
        crossed[0].resetIndicators();
        crossed[1].setName(geneNameB);
        crossed[1].resetIndicators();
        
        return crossed;
    }
    
    public void evalPopulationAccumulators(String sourceName, long totalBarsRead, File statsOutput)
    {
    	BufferedWriter fileBufWriter = null;
    	
        try {
        	fileBufWriter = new BufferedWriter(new FileWriter(statsOutput, false));
            log.trace("Writing to: " + statsOutput.getAbsolutePath());
            fileBufWriter.write("Filename,ForwardBar,TotalRecords,Matches,MatchesPercent," + 
            					"Flat,FlatPercent,Errors,ErrorsPercent,Accuracy\n");
			for(int i = 0; i < props.getNumberOfPopulations(); i++)
			{
					evalInstanceAccumulators(population[i], totalBarsRead);
					long[] accumulators = population[i].accumulators;
					fileBufWriter.write(
							String.format("%s,%d,%d,%d,%.4f,%d,%.4f,%d,%.4f,%.4f\n",
										  sourceName,
										  props.getHorizonBars(i),
										  accumulators[TOTAL_RECORDS],
										  accumulators[GAEngine.MATCHES],
										  (double) accumulators[GAEngine.MATCHES] * 100 / 
										  		   accumulators[TOTAL_RECORDS],
										  accumulators[GAEngine.FLAT],
										  (double) accumulators[GAEngine.FLAT] * 100 /
										  		   accumulators[TOTAL_RECORDS],
										  accumulators[GAEngine.ERRORS],
										  (double) accumulators[GAEngine.ERRORS] * 100 /
										  		   accumulators[GAEngine.TOTAL_RECORDS],
										  (double) accumulators[GAEngine.MATCHES] * 100 /
										  		   (accumulators[GAEngine.TOTAL_RECORDS] - accumulators[GAEngine.FLAT])
										 ));
			}
			fileBufWriter.flush();
			fileBufWriter.close();
        } 
        catch (Exception e) {
            log.error("Unable to open file for writing", e);
            return;
        }
    }

    public void evalInstanceAccumulators(PopulationInstance populationInstance, long totalBarsRead)
    {
    	Gene g = populationInstance.arbitrator;
    	populationInstance.accumulators[GAEngine.TOTAL_RECORDS] = totalBarsRead;
		populationInstance.accumulators[GAEngine.MATCHES] = g.getTotalBarsSurviving();
		populationInstance.accumulators[GAEngine.ERRORS] = g.getTotalBarsSurviving() -
														   g.getTotalWin();
		populationInstance.accumulators[GAEngine.FLAT] = g.getTotalBarsSurviving() -
														 g.getTotalLong() -
														 g.getTotalShort();
    }

    public List<Gene>getRank(int i) {
		return ranks[i];
	}
	
	public PopulationInstance getPopulation(int i)
	{
		return population[i];
	}
}
