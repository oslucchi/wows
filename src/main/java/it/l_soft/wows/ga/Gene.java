package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.indicators.IndicatorContext;
import it.l_soft.wows.utils.RingBuffer;
import it.l_soft.wows.utils.RingBuffer.ConsumerHandle;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;

public final class Gene implements GeneInterface {
	public class ScoreHolder {
		public String name = "";
		public long timestamp = 0;
		public double score = 0;
		public double predictedMarketPrice = 0;
		public boolean successful = false;
		public int direction = 0;
	}
    private static final double SIGNAL_MAX_ABS = 50.0;

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
    private RingBuffer<ScoreHolder> scores;
    private RingBuffer<ScoreHolder>.ConsumerHandle scoresReader;
    
    private ScoreHolder prediction;

    public Gene(String name, int[] indicatorIndices, double[] weights) {
        this.name = name;
        this.indicatorIndices = indicatorIndices;
        this.weights = weights;
        this.scores = new RingBuffer<Gene.ScoreHolder>(50);
        scoresReader = scores.createConsumer();
    }
    
    public void evaluateScorePrediction(MarketBar bar,
    		int marketDirection,
    		double predictedMoveNorm,
    		double denom)    // <-- new parameter
    				throws MissedItemsException
    {
    	if (scores.getLength() > 0) {
    		prediction = scoresReader.poll().getValue();
    		int agreeOnDirection = marketDirection * prediction.direction;

    		double distance = Math.abs(prediction.predictedMarketPrice - bar.getClose());
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
    				longWin--;
    			}
    			else
    			{
    				totalShort++;
    				shortWin--;
    			}
    			prediction.successful = false;
    		}
			prediction.score = agreeOnDirection / (distance + 1e-9); // optional epsilon for safety
    		totalScore += prediction.score;
    		totalWin += agreeOnDirection;
    	}

    	prediction = new ScoreHolder();
    	prediction.timestamp = bar.getTimestamp();

    	// convert from normalized prediction back to real return
    	double predictedReturn = predictedMoveNorm * denom; // e.g. +/- 1 * 0.01 = +/-1%
    	prediction.predictedMarketPrice = bar.getClose() * (1.0 + predictedReturn);

    	prediction.direction = (Math.signum(predictedMoveNorm) >= 0) ? 1 : -1;
    	this.scores.publish(prediction);
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
	
}
