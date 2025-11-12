package it.l_soft.wows.ga;

import java.util.Arrays;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;

public final class Gene {
	private final Logger log = Logger.getLogger(this.getClass());

	ApplicationProperties props = ApplicationProperties.getInstance();
	private final class PredictionScore {
		public int nextBarPrediction = 0;
		public int score = 0;
		
	}
    public final int[] indicatorsIndex; // chosen by index into your master list
    public int barsSeen = 0;
    private int howManyScoresRecorded = 0;
	public String name = "";

    // online score accumulator
    public int score = 0;
    public PredictionScore[] predictionHistory;

    public Gene(int[] indicatorsIndex) {
        this.indicatorsIndex = indicatorsIndex;
        this.predictionHistory = new PredictionScore[props.getValidScoreHistoryLength()];
        for(int i = 0; i < props.getValidScoreHistoryLength(); i++)
        {
    		predictionHistory[i] = new PredictionScore();
        }
    }

    public Gene copyShallow() {
        return new Gene(Arrays.copyOf(indicatorsIndex, indicatorsIndex.length));
    }
    
    // The prediction score a is an array ring.
    // The new score is always added on the virtual head.
    // the sum of the scores tells us the lass x bars behaviour of this gene
    public void addScore(int marketMove, int prediction)
    {
    	barsSeen++;
    	if (!warmedUp()) return;
    	
    	PredictionScore item = predictionHistory[howManyScoresRecorded];
    	log.trace(name + ": previous gene's score value " + this.score);
    	this.score -= item.score;
    	log.trace("current bar prediction " + item.nextBarPrediction + 
    			  " vs marketMove: " + marketMove);
    	item.score = Math.abs(item.nextBarPrediction + marketMove);
    	if ((marketMove != 0) && 
    		(Math.signum(item.nextBarPrediction) != Math.signum(marketMove)))
    	{
    		item.score *= -1;
    	}
    	log.trace("current bar score " + item.score);
    	this.score += item.score;
    	log.trace("current gene's score value " + this.score);
    	++howManyScoresRecorded;
    	// move to next item and set the predicted value
    	howManyScoresRecorded = howManyScoresRecorded % props.getValidScoreHistoryLength();
    	predictionHistory[howManyScoresRecorded].nextBarPrediction = prediction;
    }
    
    public boolean warmedUp()
    {
    	return (barsSeen > props.getMinBarsBeforeScoring());
    }
}
