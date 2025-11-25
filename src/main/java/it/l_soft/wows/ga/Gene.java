package it.l_soft.wows.ga;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.IndicatorContext;
import it.l_soft.wows.utils.RingBuffer;
import it.l_soft.wows.utils.RingBuffer.ConsumerHandle;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;
import it.l_soft.wows.utils.TextFileHandler;

public final class Gene implements GeneInterface {
	public class ScoreHolder {
		public String name = "";
		public long timestamp = 0;
		public double score = 0;
		public double predictedMarketPrice = 0;
		public boolean successful = false;
		public int direction = 0;
		public long barNumber = 0;
	}
    private static final double SIGNAL_MAX_ABS = 50.0;

	private final Logger log = Logger.getLogger(this.getClass());
    private ApplicationProperties props = ApplicationProperties.getInstance();

    private String name;
    private int[] indicatorIndices;
    private double[] weights;
    private double totalScore;
    private int totalWin = 0;
    private int longWin = 0;
    private int shortWin = 0;
    private int	totalLong = 0;
    private int totalShort = 0;
    private long totalBarsSurviving = 0;
    private int belongsToPopulation = -1;
    private RingBuffer<ScoreHolder> scores;
    private RingBuffer<ScoreHolder>.ConsumerHandle scoresReader;
    
    private ScoreHolder prediction;

    public Gene(String name, int[] indicatorIndices, double[] weights, int population) {
        this.name = name;
        this.indicatorIndices = indicatorIndices;
        this.weights = weights;
        this.scores = new RingBuffer<Gene.ScoreHolder>(50);
        scoresReader = scores.createConsumer();
        this.belongsToPopulation = population;
    }
       
    public void geneEvalMaths(Gene g, List<Indicator> indicators, MarketBar currBar, 
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
    
    public void evaluateScorePrediction(MarketBar currBar, 
    									MarketBar prevBar,
							    		int marketDirection,
							    		double predictedMoveNorm,
							    		double denom,
							    		String name)
    				throws MissedItemsException
    {
    	StringBuilder sb = new StringBuilder();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.sss");
    	totalBarsSurviving++;

    	if (scores.getLength() >= props.getHorizonBars(belongsToPopulation)) {
    		try {
        		prediction = scoresReader.poll().getValue();
    		}
    		catch(MissedItemsException e)
    		{
    			log.error("Gene " + name + ", error " + e.getMessage() + " on poll", e);
    		}

    		// if we have at least one prediction, the last should be referred to the current bar
			sb.append(String.format("%s,%s,%d,",
    								name, this.name, totalBarsSurviving));
			sb.append(String.format("%s,%d,%d,%s - %s,",
									sdf.format(new Date(currBar.getTimestamp())), currBar.getBarNumber(),
									marketDirection,
				    				(marketDirection >= 0 ? "LONG" : "SHORT"), 
									(prediction.direction >= 0 ? "LONG" : "SHORT")
								   ));
			
    		sb.append(String.format("%.4f,%.4f,%.4f,%.4f,%d,%.4f,",
				    				prevBar.getClose(), currBar.getClose(), prediction.predictedMarketPrice,
									predictedMoveNorm,
									(predictedMoveNorm >= 0. ? 1 : -1),
									denom));

    		int agreeOnDirection = marketDirection * prediction.direction;
    		double distance = Math.abs(prediction.predictedMarketPrice - currBar.getClose());
    		
    		if (agreeOnDirection >= 0) {
    			if (prediction.direction > 0)
    			{
    				totalLong++;
    				longWin++;
    			}
    			else
    			{
    				totalShort++;
    				shortWin++;
    			}
    			prediction.successful = true;
    		} 
    		else {
    			if (prediction.direction > 0)
    			{
    				totalLong++;
    			}
    			else
    			{
    				totalShort++;
    			}
    			prediction.successful = false;
    		}

//    		prediction.score = Math.max(bar.getClose() * .05, denom / (distance + 1e-9)); // optional epsilon for safety
    		prediction.score = Math.min(currBar.getClose() * .05, 1 / (distance + 1e-9)) * // optional epsilon for safety
    						   agreeOnDirection; 
        	prediction.timestamp = currBar.getTimestamp();
    		totalScore += prediction.score;
    		totalWin += agreeOnDirection;
    		
    		sb.append(String.format("%.4f,", prediction.score));
    		if (name.compareTo("arbitrator") == 0)
    		{
    			try {
		        	TextFileHandler temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
		        											   props.getGeneEvalDumpName() + "_arbi_" + belongsToPopulation, 
		        											   "csv", false, true);
					temp.write(sb.toString(), true);
					temp.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}

    	if (name.compareTo("arbitrator") != 0)
    	{
        	prediction = new ScoreHolder();
        	prediction.name = name;
        	prediction.timestamp = currBar.getTimestamp();
        	prediction.barNumber = currBar.getBarNumber() + 1;

        	// convert from normalized prediction back to real return
        	double predictedReturn = predictedMoveNorm * denom; // e.g. +/- 1 * 0.01 = +/-1%
        	prediction.predictedMarketPrice = currBar.getClose() * (1.0 + predictedReturn);
        	prediction.direction = (Math.signum(predictedMoveNorm) >= 0) ? 1 : -1;
    		this.scores.publish(prediction);
    	}
    }


	public ScoreHolder getPrediction(long barNum) 
			throws MissedItemsException {
		return scoresReader.get(barNum).getValue();
	}
	
    /** Raw gene signal as weighted sum of normalized indicators, clamped to [-50, 50]. */
    @Override
    public double computeSignal(int t, IndicatorContext ctx) {
        double sum = 0.0;
        for (int i = 0; i < indicatorIndices.length; i++) {
            double x = ctx.getNormalizedIndicator(indicatorIndices[i], t);
            sum += weights[i] * x;
        }
        return clamp50(sum);
    }

    public RingBuffer<ScoreHolder> getScores()
    {
    	return scores;
    }
    
    @SuppressWarnings("rawtypes")
	public ConsumerHandle getReader() {
    	return scoresReader;
    }
    
    /**
     * Prediction of normalized market move, on the SAME SCALE as marketMoveNorm: [-1, 1].
     * This is what you compare against marketMoveNorm.
     */
    public double computePredictedMoveNorm(int t, IndicatorContext ctx) {
        double raw = computeSignal(t, ctx);       // [-50, 50]
        double scaled = raw / SIGNAL_MAX_ABS;     // [-1, 1] if raw fully spans ±50
        return clamp1(scaled);
    }

    private static double clamp50(double x) {
        if (x >  SIGNAL_MAX_ABS) return  SIGNAL_MAX_ABS;
        if (x < -SIGNAL_MAX_ABS) return -SIGNAL_MAX_ABS;
        return x;
    }

    private static double clamp1(double x) {
        if (x >  1.0) return  1.0;
        if (x < -1.0) return -1.0;
        return x;
    }
    
    public void resetIndicators()
    {
        totalScore = 0;
        totalWin = 0;
        longWin = 0;
        shortWin = 0;
        totalLong = 0;
        totalShort = 0;
        totalBarsSurviving = 0;
        scores = new RingBuffer<Gene.ScoreHolder>(50);
        scoresReader = scores.createConsumer();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[] getIndicatorIndices() {
        return indicatorIndices;
    }

    public void setIndicatorIndices(int[] indicatorIndices) {
        this.indicatorIndices = indicatorIndices;
    }

    public double[] getWeights() {
        return weights;
    }

    public double getTotalScore() {
        return totalScore;
    }

	public int getWinAccumulator() {
		return totalWin;
	}

	public long getScoreTimestamp()
	{
		return prediction.timestamp;
	}
	
	public boolean getScoreSuccessful()
	{
		return prediction.successful;
	}

	public boolean canPredict() {
		return scores.getLength() > props.getGenesWarmUpBars();
	}

	public int getLongWin() {
		return longWin;
	}

	public int getShortWin() {
		return shortWin;
	}

	public int getTotalLong() {
		return totalLong;
	}

	public int getTotalShort() {
		return totalShort;
	}

	public void setLongWin(int longWin) {
		this.longWin = longWin;
	}

	public void setShortWin(int shortWin) {
		this.shortWin = shortWin;
	}

	public void setTotalLong(int totalLong) {
		this.totalLong = totalLong;
	}

	public void setTotalShort(int totalShort) {
		this.totalShort = totalShort;
	}

	public double getWinRate() {
		if (totalLong + totalShort > 0)
		{
			return totalWin / (totalLong + totalShort);
		}
		else
		{
			return 0;
		}
	}

	public long getTotalBarsSurviving() {
		return totalBarsSurviving;
	}

	public void setTotalBarsSurviving(long totalBarsSurviving) {
		this.totalBarsSurviving = totalBarsSurviving;
	}
	
}
