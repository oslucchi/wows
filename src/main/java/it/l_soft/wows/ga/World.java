package it.l_soft.wows.ga;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;
import it.l_soft.wows.utils.TextFileHandler;

public class World {
    private final Logger log = Logger.getLogger(this.getClass());
	private static ApplicationProperties props = ApplicationProperties.getInstance();
    public static final int TOTAL_RECORDS = 0;
    public static final int MATCHES = 1;
    public static final int FLAT = 2;
    public static final int ERRORS = 3;	
    public static final int TOTAL_ACCUMULATORS = 4;
    

	private List<Gene> genes, roundRank;
	private long[] accumulators;
	private Gene arbitrator;
	private int horizonBars;
	private int size;
	private int populationId;
	private TextFileHandler resultsOut;
	
	public World() {}
			
	public int getHorizonBars() {
		return horizonBars;
	}

	public List<Gene> getGenes() {
		return genes;
	}

	public long[] getAccumulators() {
		return accumulators;
	}

	public Gene getArbitrator() {
		return arbitrator;
	}

	public void setArbitrator(Gene arbitrator) {
		this.arbitrator = arbitrator;
	}

	public int getSize() {
		return size;
	}

	public int getPopulationId() {
		return populationId;
	}
	
    public static World initPopulation(int populationId, List<Gene> genes, int horizonBars) 
    		throws Exception 
    {
    	World instance = new World();
    	instance.genes = genes;
    	instance.size = genes.size();
        instance.horizonBars = horizonBars;
        instance.accumulators = new long[TOTAL_ACCUMULATORS];
        instance.populationId = populationId;
        instance.resultsOut = new TextFileHandler(props.getCSVFilePath(), "prediction_" + populationId, "csv");
        instance.resultsOut.write("Gene,Indicators,BarsSurvived,MktTS," +
    						 "MktBar#,MktDir,PredDir," +
    						 "PrevClose,CurrClose,PredMktPrice,PredMove," + 
    						 "Denom,Direction (M-P)", true);
        for(Gene g : genes)
        {
        	if(g.getName().compareTo("arbitrator") == 0)
        	{
        		instance.arbitrator = g;
        		break;
        	}
        }
        return instance;
    }
    
    
    public void evaluateWorldMembers(List<Indicator> indicators, MarketBar currBar, 
    					  			 MarketBar prevBar, double denom) 
			throws Exception
    {
    	// do the evaluation math on each gene
    	double yhat;
    	int marketDirection;
    	if (currBar.getClose() >= prevBar.getClose())
    	{
    		marketDirection = 1;
    	}
    	else
    	{
    		marketDirection = -1;
    	}

    	for (Gene g : this.genes) 
    	{
       	    double z = 0.0;
       	    for (int i : g.getIndicators()) 
       	    {
       	        // each indicator normalized to [-50,50] → divide by 50 → [-1,1]
       	        z += indicators.get(i).getNormalizedValue() / 50.0;
       	    }
       	    z /= Math.max(1, g.getIndicators().length); // average
    		yhat = Math.tanh(z / props.getPredictionTemperature());

    		if (g.getName().compareTo("arbitrator") == 0)
    		{
    			addArbitratorsPrediction(currBar);
    		}
    		else
    		{
    			addGenesPrediction(g, currBar, yhat, denom);
    		}
    		
    		g.setTotalBarsSurviving(g.getTotalBarsSurviving() + 1);
    		if (currBar.getBarNumber() > this.horizonBars)
    		{
    			scoreGenesPrediction(g, marketDirection, currBar, prevBar, yhat, denom, g.getName());
    		}    		
    	}
    }

    private List<Gene> rank(List<Gene> population) {
        final double TOTAL_WEIGHT = props.getWaitOfScoreInRanking();
        final double WINRATE_WEIGHT = props.getWaitOfWinRateInRanking();
        List<Gene> toSort = population.stream()
        						.filter(g -> !g.getName().equals("arbitrator"))
        	    				.collect(Collectors.toList());
        
        List<Gene> sorted =  toSort.stream()
                .sorted(Comparator.comparingDouble((Gene g) -> {
                    double totalScore = g.getTotalScore() / g.getTotalBarsSurviving();
                    double winRate = g.getWinRate() / g.getTotalBarsSurviving();
                    return totalScore * TOTAL_WEIGHT + winRate * WINRATE_WEIGHT;
                }).reversed())
                .collect(Collectors.toList());
        sorted.add(arbitrator);
        return sorted;
    }
    
    private void scoreGenesPrediction(Gene g,
    									int marketDirection,
    									MarketBar currBar, 
							    		MarketBar prevBar,
							    		double predictedMoveNorm,
							    		double denom,
							    		String name)
    				throws MissedItemsException, Exception
    {
    	Prediction prediction = null;

    	if ((g.getScores().getLength() > this.horizonBars) &&
    		(g.getTotalBarsSurviving() > g.getScores().getLength()))
    	{
			if ((prediction = g.getPrediction(currBar.getBarNumber())) == null)
			{
    			throw new Exception("Prediction for bar " + 
    								(currBar.getBarNumber() - this.horizonBars) + 
    								" not found as expetecd at " + currBar.getBarNumber());
    		}

			if (prediction.getPredictedBarNumber() != currBar.getBarNumber())
    		{
    			// TODO: Handle discrepancy in bar predicted number
    			throw new Exception("Got prediction from different bar than current. " +
    								"CurrentBar " + currBar.getBarNumber() + " " +
    								"Prediction was for " + prediction.getPredictedBarNumber());
    		}

    		// if we have at least one prediction, the last should be referred to the current bar
    		int agreeOnDirection = marketDirection * prediction.getExpectedDirection();
    		double distance = Math.abs(prediction.getPredictedMarketPrice() - currBar.getClose());

    		if (agreeOnDirection != 0)
    		{
    			if (agreeOnDirection > 0) {
    				if (prediction.getExpectedDirection() > 0)
    				{
    					g.setTotalLong(g.getTotalLong() + 1);
    					g.setLongWin(g.getLongWin() + 1);
    				}
    				else
    				{
    					g.setTotalShort(g.getTotalShort() + 1);
    					g.setShortWin(g.getShortWin() + 1);
    				}
    				prediction.setSuccessful(true);
    			} 
    			else {
    				if (prediction.getExpectedDirection() > 0)
    				{
    					g.setTotalLong(g.getTotalLong() + 1);
    				}
    				else
    				{
    					g.setTotalShort(g.getTotalShort() + 1);
    				}
    				prediction.setSuccessful(false);
    			}
    		}
    		else
    		{
    			;
    		}
    		//prediction.score = Math.max(bar.getClose() * .05, denom / (distance + 1e-9)); // optional epsilon for safety
    		prediction.setScore(
    				Math.min(currBar.getClose() * .05, 
    						 1 / (distance + 1e-9)) * agreeOnDirection // optional epsilon for safety
    							);

    		g.setTotalScore(g.getTotalScore() + prediction.getScore());
    		if (prediction.isSuccessful()) 
    		{
    			g.setTotalWin(g.getTotalWin() + 1);
    		}
    		accumulators[TOTAL_RECORDS] = arbitrator.getTotalBarsSurviving();
    		accumulators[MATCHES] = arbitrator.getTotalLong() + arbitrator.getTotalShort();
    		accumulators[FLAT] = accumulators[TOTAL_RECORDS] - accumulators[MATCHES];
    		accumulators[ERRORS] = accumulators[MATCHES] - (arbitrator.getLongWin() + arbitrator.getShortWin());
        	dumpResults(g, currBar, prevBar, denom, marketDirection, prediction);
    	}
    	
    	return;
    }
    
    private void addGenesPrediction(Gene g, MarketBar currBar, double predictedMoveNorm, double denom)
    {
		Prediction prediction = new Prediction(currBar, currBar.getBarNumber() + this.horizonBars);

		// convert from normalized prediction back to real return
		double predictedReturn = predictedMoveNorm * denom; // e.g. +/- 1 * 0.01 = +/-1%
		prediction.setPredictedMarketPrice(currBar.getClose() * (1.0 + predictedReturn));
		prediction.setExpectedDirection(
				(int) (Math.abs(predictedReturn) < props.getHoldThresholdPct() ? 
						0 
						:
							(predictedMoveNorm >= 0. ? 1 : -1)
						)
				);
		g.getScores().publish(prediction);
    }

    private void addArbitratorsPrediction(MarketBar currBar)
    {
		// rank the population after evaluating their score
    	// The arbitrator will be removed from the list by the ranking function 
    	// before applying the sort criteria and re-added as the last item after the sort
    	// is performed
		roundRank = rank(this.genes);
	
		// Set the new prediction for predictor
		Gene firstInRank = roundRank.getFirst();
		
		Prediction firstInRankScore;
		@SuppressWarnings("unchecked")
		List<Prediction> scoresAsList = firstInRank.getReader().getContentAsList();
		firstInRankScore = scoresAsList.getLast();

		Prediction newPrediction = new Prediction(currBar, currBar.getBarNumber() + this.horizonBars);
	
		arbitrator.setIndicators(firstInRank.getIndicators());
		newPrediction.setExpectedDirection(firstInRankScore.getExpectedDirection());
		newPrediction.setPredictedMarketPrice(firstInRankScore.getPredictedMarketPrice());
	
		arbitrator.getScores().publish(newPrediction);
	
		return;
	}


	public TextFileHandler getResultsOut() {
		return resultsOut;
	}

	public void setResultsOut(TextFileHandler resultsOut) {
		this.resultsOut = resultsOut;
	}

    public void dumpAccumulators(String sourceName, TextFileHandler statsOutput)
    {    	
        try {
			statsOutput.write(
					String.format("%s,%d,%d,%d,%.4f,%d,%.4f,%d,%.4f,%.4f",
								  sourceName,
								  this.horizonBars,
								  accumulators[TOTAL_RECORDS],
								  accumulators[MATCHES],
								  (double) accumulators[MATCHES] * 100 / 
								  		   accumulators[TOTAL_RECORDS],
								  accumulators[FLAT],
								  (double) accumulators[FLAT] * 100 /
								  		   accumulators[TOTAL_RECORDS],
								  accumulators[ERRORS],
								  (double) accumulators[ERRORS] * 100 /
								  		   accumulators[TOTAL_RECORDS],
								  (double) (accumulators[MATCHES] - accumulators[ERRORS]) * 100 /
								  		   accumulators[MATCHES]
								 ),
					  true);
        } 
        catch (Exception e) {
            log.error("Unable to open file for writing", e);
            return;
        }   
    }
    
    private void dumpResults(Gene g, MarketBar currBar, MarketBar prevBar, double denom,
    						 int marketDirection, Prediction prediction) 
    	throws IOException
    {
    	if ((g.getName().compareTo("arbitrator") != 0) && !props.getDumpGenes()) return;
    	
    	SimpleDateFormat sdf = new SimpleDateFormat(props.getTimestampFormat());

    	StringBuilder sb = new StringBuilder();
		sb.append(String.format("%s,%s,%d,",
								g.getName(), g.getBasedOn(), g.getTotalBarsSurviving()));
		sb.append(String.format("%s,%d,%d,%d,",
								sdf.format(new Date(currBar.getTimestamp())), 
								currBar.getBarNumber(),
								marketDirection,
								prediction.getExpectedDirection()
								)
				);

		sb.append(String.format("%.4f,%.4f,%.4f,%.4f,%.4f,%s - %s,",
								prevBar.getClose(), 
								currBar.getClose(), 
								prediction.getPredictedMarketPrice(),
								prediction.getPredictedMoveNorm(),
								denom,
								(marketDirection > 0 ? "LONG" : (marketDirection == 0 ? "FLAT" : "SHORT")), 
								(prediction.getExpectedDirection() > 0 ? "LONG" : (prediction.getExpectedDirection() == 0 ? "FLAT" : "SHORT"))
								)
				);
		sb.append(String.format("%.4f,", prediction.getScore()));
		resultsOut.write(sb.toString(), true);
    }

}
