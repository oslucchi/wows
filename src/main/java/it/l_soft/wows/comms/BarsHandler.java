package it.l_soft.wows.comms;

import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.ga.World;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.RingBuffer;

public class BarsHandler {
    private final Logger log = Logger.getLogger(this.getClass());
	private ApplicationProperties props = ApplicationProperties.getInstance();
	private long barNumber = 0;
	private RingBuffer<MarketBar> marketBars = new RingBuffer<MarketBar>(props.getBarsInMemory());
	private RingBuffer<MarketBar>.ConsumerHandle marketBarConsumer = marketBars.createConsumer();
	private ATR atrRef;
	private List<Indicator> indicators;
    private World[] worlds;


	
	public BarsHandler(ATR atrRef, List<Indicator> indicators, World[] worlds) {
		this.atrRef = atrRef;
		this.indicators = indicators;
		this.worlds = worlds;
	}

    public void handleBar(MarketBar bar) 
    		throws Exception
    {
    	MarketBar currBar;
        currBar = bar;
        currBar.setBarNumber(barNumber);
        marketBars.publish(currBar);
        
        // Pass the current bar to the indicators to recalculate their values
        // use it each world to update the genes prediction and score them
        log.debug("New bar received: BarNumber " + barNumber + " Close " + currBar.getClose() );
                
        // --- 1) Update indicators (ensure ATR is updated first for scaling) ---
        if (atrRef != null) {
            atrRef.add(currBar);
            atrRef.normalizeAndStore(currBar, atrRef, props);
        }
        
        // Update indicators with current bar
        for (Indicator indicator : indicators) {
            if (indicator == atrRef) continue;
            indicator.add(currBar);
            indicator.normalizeAndStore(currBar, atrRef, props);
            log.trace("Indicator: " + indicator.getClass().getSimpleName() +
                      ", normalizedValue: " + indicator.getNormalizedValue());
        }
        
    	barNumber++;
        if (currBar.getBarNumber() == 0)
        {
        	// nothing could be done on the very first bar
        	return;
        }
        else if (currBar.getBarNumber() < props.getGenesWarmUpBars())
        {
        	// do not consider this yet for gene evaluation and world scoring
        	return;
        }
        
        for(World world : worlds)
        {
        	if (currBar.getBarNumber() < world.getHorizonBars() + 1) continue; // not enough history 
     
        	if (marketBarConsumer.get(currBar.getBarNumber() - world.getHorizonBars()) == null)
        	{
        		//  TODO Handle Exception
        		continue;
        	}
        	MarketBar prevBar = marketBarConsumer.get(currBar.getBarNumber() - world.getHorizonBars()).getValue();
        	
            double atrAbs = (atrRef != null) ? atrRef.value() : Double.NaN;
            double atrPct = (!Double.isNaN(atrAbs) && prevBar.getClose() != 0.0) ? (atrAbs / prevBar.getClose()) : 0.0;
            double denom = Math.max(1e-9, props.getVolNormK() * Math.max(1e-9, atrPct));
            
            world.evaluateWorldMembers(indicators, currBar, prevBar, denom);
        }
    }

}
