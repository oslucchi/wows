package it.l_soft.wows.utils;

import java.util.Map;

import it.l_soft.wows.comms.MarketBar;
import it.l_soft.wows.comms.Message;
import it.l_soft.wows.comms.TradeMessage;

public class Utilities {
	public static final Map<String, Class<? extends Message>> MESSAGE_TYPES =
		    Map.ofEntries(
		        Map.entry("A", TradeMessage.class),
		        Map.entry("B", MarketBar.class)
		    );

	public static void pause(int i) {
		try {
			Thread.sleep(i);
		}
		catch(Exception e)
		{
			;
		}
	}
}