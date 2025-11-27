package it.l_soft.wows.ga;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.ga.Gene.ScoreHolder;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;

public class GeneEvaluator {
	private final Logger log = Logger.getLogger(this.getClass());

	ApplicationProperties props = ApplicationProperties.getInstance();
	Gene g;
	List<Indicator> indicators;
	PopulationInstance populationInstance;
	
	public GeneEvaluator(Gene g, List<Indicator> indicators, PopulationInstance populationInstance)
	{
		this.g = g;
		this.indicators = indicators;
		this.populationInstance = populationInstance;
	}
	
	
	public double predictMarketMovement()
	{
   	    double z = 0.0;
   	    for (int i : g.getIndicatorIndices()) 
   	    {
   	        // each indicator normalized to [-50,50] → divide by 50 → [-1,1]
   	        z += indicators.get(i).getNormalizedValue() / 50.0;
   	    }
   	    z /= Math.max(1, g.getIndicatorIndices().length); // average
   	    return Math.tanh(z / props.getPredictionTemperature()); // yhat 
	}
	
	
    public String evaluateScorePrediction(MarketBar currBar, 
							    		MarketBar prevBar,
							    		double predictedMoveNorm,
							    		double denom,
							    		String name)
			throws MissedItemsException
    {
    	StringBuilder sb = new StringBuilder();
        ScoreHolder prediction = null;
    	SimpleDateFormat sdf = new SimpleDateFormat(props.getTimestampFormat());
    	
    	int marketDirection;
    	if (Math.abs(currBar.getClose() - prevBar.getClose()) / currBar.getClose() 
    			< props.getMinimalPriceChangeForDirection())
    	{
    		marketDirection = 0;
    	}
    	else if (currBar.getClose() > prevBar.getClose())
    	{
    		marketDirection = 1;
    	}
    	else
    	{
    		marketDirection = -1;
    	}
    		    	
    	g.setTotalBarsSurviving(g.getTotalBarsSurviving() + 1);
    	    	
    	if (g.getScores().getLength() >= populationInstance.horizonBars) 
    	{
    		try {
    			prediction = (ScoreHolder) g.getReader().poll().getValue();
    		}
    		catch(MissedItemsException e)
    		{
    			log.error("Gene " + name + ", error " + e.getMessage() + " on poll", e);
    			throw e;
    		}
    		
    		String indicatorsUsed = "";
    		for(int indicatorIdx : g.getIndicatorIndices())
    		{
    			indicatorsUsed += indicators.get(indicatorIdx).getName() + " ";
    		}
    		
    		// if we have at least one prediction, the last should be referred to the current bar
    		sb.append(String.format("%s,%s,%d,",
    								name, indicatorsUsed, g.getTotalBarsSurviving()));
    		sb.append(String.format("%s,%d,%d,%s - %s,",
				    				sdf.format(new Date(currBar.getTimestamp())), currBar.getBarNumber(),
				    				marketDirection,
				    				(marketDirection > 0 ? "LONG" : (marketDirection == 0 ? "FLAT" : "SHORT")), 
				    				(prediction.direction > 0 ? "LONG" : (prediction.direction == 0 ? "FLAT" : "SHORT"))
				    			   ));

    		sb.append(String.format("%.4f,%.4f,%.4f,%.4f,%d,%.4f,",
				    				prevBar.getClose(), currBar.getClose(), prediction.predictedMarketPrice,
				    				predictedMoveNorm,
				    				(predictedMoveNorm >= 0. ? 1 : -1),
				    				denom));

    		int agreeOnDirection = marketDirection * prediction.direction;
    		double distance = Math.abs(prediction.predictedMarketPrice - currBar.getClose());

    		if (agreeOnDirection != 0)
    		{
    			if (agreeOnDirection > 0) {
    				if (prediction.direction > 0)
    				{
    					g.setTotalLong(g.getTotalLong() + 1);
    					g.setLongWin(g.getLongWin() + 1);
    				}
    				else
    				{
    					g.setTotalShort(g.getTotalShort() + 1);
    					g.setShortWin(g.getShortWin() + 1);
    				}
    				prediction.successful = true;
    			} 
    			else {
    				if (prediction.direction > 0)
    				{
    					g.setTotalLong(g.getTotalLong() + 1);
    				}
    				else
    				{
    					g.setTotalShort(g.getTotalShort() + 1);
    				}
    				prediction.successful = false;
    			}
    		}
    		else
    		{
    			;
    		}


    		//prediction.score = Math.max(bar.getClose() * .05, denom / (distance + 1e-9)); // optional epsilon for safety
    		prediction.score = Math.min(currBar.getClose() * .05, 
    									1 / (distance + 1e-9)) * agreeOnDirection;// optional epsilon for safety

    		prediction.timestamp = currBar.getTimestamp();
    		g.setTotalScore(g.getTotalScore() + prediction.score);
    		g.setTotalWin(g.getTotalWin() + (agreeOnDirection == 1 ? 1 : 0));

    		sb.append(String.format("%.4f,", prediction.score));
    	}

    	if ((name != null) && (name.compareTo("arbitrator") != 0))
    	{
    		prediction = g.new ScoreHolder();
    		prediction.name = name;
    		prediction.timestamp = currBar.getTimestamp();
    		prediction.barNumber = currBar.getBarNumber() + 1;

    		// convert from normalized prediction back to real return
    		double predictedReturn = predictedMoveNorm * denom; // e.g. +/- 1 * 0.01 = +/-1%
    		prediction.predictedMarketPrice = currBar.getClose() * (1.0 + predictedReturn);
    		prediction.direction = (int) (Math.abs(predictedReturn) < props.getMinimalPriceChangeForDirection() ? 
    									  	0 
    									  :
    										(Math.signum(predictedReturn) >= 0 ? 1 : -1)
    									  );
    		g.getScores().publish(prediction);
    	}
    	
    	return sb.toString();
	}
}
