package it.l_soft.wows.ga;

import it.l_soft.wows.comms.Bar;

public class Prediction {
	private Bar barAtPrediction;
	private long predictedBarNumber = 0;
	private int expectedDirection = 0;
	private double predictedMove = 0;
	private double predictedMoveNorm = 0;
	private double predictedMarketPrice = 0;
	private boolean successful = false;
	private double score = 0;
	
	public Prediction(Bar barAtPrediction, long predictedBarNumber) {
		super();
		this.barAtPrediction = barAtPrediction;
		this.predictedBarNumber = predictedBarNumber;
	}

	public Bar getBarAtPrediction() {
		return barAtPrediction;
	}

	public void setBarAtPrediction(Bar barAtPrediction) {
		this.barAtPrediction = barAtPrediction;
	}

	public long getPredictedBarNumber() {
		return predictedBarNumber;
	}

	public void setPredictedBarNumber(long predictedBarNumber) {
		this.predictedBarNumber = predictedBarNumber;
	}

	public int getExpectedDirection() {
		return expectedDirection;
	}

	public void setExpectedDirection(int expectedDirection) {
		this.expectedDirection = expectedDirection;
	}

	public double getPredictedMarketPrice() {
		return predictedMarketPrice;
	}

	public void setPredictedMarketPrice(double predictedMarketPrice) {
		this.predictedMarketPrice = predictedMarketPrice;
	}

	public boolean isSuccessful() {
		return successful;
	}

	public void setSuccessful(boolean successful) {
		this.successful = successful;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public double getPredictedMove() {
		return predictedMove;
	}

	public void setPredictedMove(double predictedMove) {
		this.predictedMove = predictedMove;
	}

	public double getPredictedMoveNorm() {
		return predictedMoveNorm;
	}

	public void setPredictedMoveNorm(double predictedMoveNorm) {
		this.predictedMoveNorm = predictedMoveNorm;
	}
}
