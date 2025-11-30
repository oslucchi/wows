package it.l_soft.wows.ga;

import java.util.List;

//import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.IndicatorContext;
import it.l_soft.wows.utils.RingBuffer;
import it.l_soft.wows.utils.RingBuffer.ConsumerHandle;

public final class Gene implements GeneInterface {
//	private final Logger log = Logger.getLogger(this.getClass());
    private ApplicationProperties props = ApplicationProperties.getInstance();

    private static final double SIGNAL_MAX_ABS = 50.0;
    
    private String name;
    private int[] indicators;
    private double[] weights;
    private double totalScore;
    private int totalWin = 0;
    private int longWin = 0;
    private int shortWin = 0;
    private int	totalLong = 0;
    private int totalShort = 0;
    private long totalBarsSurviving = 0;
    private RingBuffer<Prediction> predictions;
    private RingBuffer<Prediction>.ConsumerHandle predictionsReader;
    private List<Indicator> catalog;
    
    private Prediction prediction;

    public Gene(String name, List<Indicator> catalog, int[] indicatorIndices, double[] weights) {
        this.name = name;
        this.catalog = catalog;
        this.indicators = indicatorIndices;
        this.weights = weights;
        this.predictions = new RingBuffer<Prediction>(50);
        this.predictionsReader = predictions.createConsumer();
    }
    
    private String evalBasedOn()
    {
    	String basedOn = "";
        for(int i = 0; i < indicators.length; i++)
        {
        	basedOn += catalog.get(indicators[i]).getName();
        }
        return basedOn;
    }
	
    /** Raw gene signal as weighted sum of normalized indicators, clamped to [-50, 50]. */
    @Override
    public double computeSignal(int t, IndicatorContext ctx) {
        double sum = 0.0;
        for (int i = 0; i < indicators.length; i++) {
            double x = ctx.getNormalizedIndicator(indicators[i], t);
            sum += weights[i] * x;
        }
        return clamp(sum, SIGNAL_MAX_ABS);
    }

    /**
     * Prediction of normalized market move, on the SAME SCALE as marketMoveNorm: [-1, 1].
     * This is what you compare against marketMoveNorm.
     */
    public double computePredictedMoveNorm(int t, IndicatorContext ctx) {
        double raw = computeSignal(t, ctx);       // [-50, 50]
        double scaled = raw / SIGNAL_MAX_ABS;     // [-1, 1] if raw fully spans ±50
        return clamp(scaled, 1.0);
    }
    
    private static double clamp(double x, double clampTo) {
        if (x >  clampTo) return  clampTo;
        if (x < -clampTo) return -clampTo;
        return x;
    }

    public boolean canPredict() {
		return predictions.getLength() > props.getGenesWarmUpBars();
	}
    
    public void resetCounters()
    {
        totalScore = 0;
        totalWin = 0;
        longWin = 0;
        shortWin = 0;
        totalLong = 0;
        totalShort = 0;
        totalBarsSurviving = 0;
        predictions = new RingBuffer<Prediction>(50);
        predictionsReader = predictions.createConsumer();
        evalBasedOn();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[] getIndicators() {
        return indicators;
    }

    public void setIndicators(int[] indicators) {
        this.indicators = indicators;
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

	public int getTotalWin() {
		return totalWin;
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

	public void setTotalWin(int totalWin)
	{
		this.totalWin = totalWin;
	}
	
    public void setTotalScore(double totalScore) 
    {
        this.totalScore= totalScore ;
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
	
    public RingBuffer<Prediction> getScores()
    {
    	return predictions;
    }
    
    @SuppressWarnings("rawtypes")
	public ConsumerHandle getReader() {
    	return predictionsReader;
    }
    
    public String getBasedOn()
    {
    	return evalBasedOn();
    }


	public Prediction getPrediction() {
		return prediction;
	}
	
	public Prediction getPrediction(long barNumber) {
		for(Prediction prediction : predictionsReader.getContentAsList())
		{
			if (prediction.getPredictedBarNumber() == barNumber)
				return prediction;
		}
		return null;
	}
    
}