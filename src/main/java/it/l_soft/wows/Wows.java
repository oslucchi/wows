package it.l_soft.wows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import it.l_soft.wows.comms.Price;
import it.l_soft.wows.comms.TradingStationInterface;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.IndicatorFactory;

public class Wows {
	public static void main(String[] args) throws InterruptedException, IOException {
		Logger log = Logger.getLogger(Wows.class);
		ApplicationProperties props = ApplicationProperties.getInstance();
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		TradingStationInterface tsi;
		boolean shuttingDown = false;
		
		Logger rootLogger = Logger.getLogger("main.java.it.l_soft.wows");
		rootLogger.setLevel(Level.TRACE); // Allow all loggers to inherit TRACE

		Appender appender = rootLogger.getAppender("console");
		if (appender instanceof ConsoleAppender) {
		    ((ConsoleAppender) appender).setThreshold(Level.TRACE); // Console outputs all logs
		}
		
		System.out.println("Logger level: " + log.getLevel());
		System.out.println("Effective level: " + log.getEffectiveLevel());
		System.out.println("Working directory: " + new java.io.File(".").getAbsolutePath());

        List<Indicator> indicators = new ArrayList<Indicator>();
        for(String token: props.getIndicatorsToInstantiate().split(";"))
        {
            token = token.trim();
            if (token.isEmpty()) continue;

            indicators.add(IndicatorFactory.createFromToken(token));
        }

		tsi = new TradingStationInterface(indicators);
		tsi.run();
		
		long now = 0;
		log.trace("Now spawinig the child thread");
		while(tsi.isAlive())
		{
			Thread.sleep(1000);
			if (!shuttingDown)
			{
				now = new Date().getTime();
				log.warn("Shutdown received");
			    String s = reader.readLine();
			    if (s.toUpperCase().compareTo("END") == 0)
			    {
			    	shuttingDown = true;
			    	tsi.shutdown();
			    	log.trace("Wating for modules to shutdown ");
			    }
		    }
			else
			{
				if ((new Date().getTime()) - now > props.getShutdownGracePeriod())
				{
					log.warn("Shutdown grace period expired. Killing the thread");
					tsi.interrupt();
				}
				else
				{
					log.warn(".");
				}
			}
		}
		log.trace("Going to exit now");
	}
}
