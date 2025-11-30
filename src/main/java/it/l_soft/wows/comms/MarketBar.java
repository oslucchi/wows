package it.l_soft.wows.comms;


public class MarketBar extends Message implements Bar {
    private double open, high, low, close;
    private long volume;
    private long barNumber = 0;
    private long timestamp = 0;
    
    public MarketBar() {
    	super("B");
    }
    
    public MarketBar(long barNumber, long timestamp, 
    				 double open, double high, double low, double close, 
    				 long volume){
    	super("B");
    	this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.barNumber = barNumber;
        this.timestamp = timestamp;
    }

    // Getters
    @Override public long getBarNumber() { return barNumber; }
	@Override public long getTimestamp() { return timestamp; }
    @Override public double getOpen() { return open; }
    @Override public double getHigh() { return high; }
    @Override public double getLow() { return low; }
    @Override public double getClose() { return close; }
    @Override public long getVolume() { return volume; }
    
	public void setOpen(double open) {
		this.open = open;
	}

	public void setHigh(double high) {
		this.high = high;
	}

	public void setLow(double low) {
		this.low = low;
	}

	public void setClose(double close) {
		this.close = close;
	}

	public void setVolume(long volume) {
		this.volume = volume;
	}

	public void setBarNumber(long barNumber) {
		this.barNumber = barNumber;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

}
