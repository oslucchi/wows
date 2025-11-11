package it.l_soft.wows.comms;


public class MarketBar extends Message implements Bar {
    private double open, high, low, close;
    private long volume;
    
    public MarketBar() {
    	super("B");
    }
    
    public MarketBar(long timestamp, double open, double high, double low, double close, long volume){
    	super("B");
    	this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Getters
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
}
