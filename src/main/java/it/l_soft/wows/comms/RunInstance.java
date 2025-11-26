package it.l_soft.wows.comms;


import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.utils.RingBuffer;

public abstract class RunInstance extends Thread {
    ApplicationProperties props = ApplicationProperties.getInstance();
    RingBuffer<MarketBar> barSeries = new RingBuffer<MarketBar>(props.getBarsInMemory());
    RingBuffer<MarketBar>.ConsumerHandle barsReader = barSeries.createConsumer();
	public abstract void shutdown();
}
