package it.l_soft.wows.comms;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.ga.GAEngine;
import it.l_soft.wows.ga.Gene;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.JSONWrapper;
import it.l_soft.wows.utils.Utilities;


public class TradingStationInterface extends Thread {
	private final Logger log = Logger.getLogger(this.getClass());
	ApplicationProperties props = ApplicationProperties.getInstance();
	long barNumber = 0;
	
	InputStream input;
	Socket socket = null;
	List<Indicator> indicators;
	GAEngine ga;
	MarketBar prevBar = null;
	MarketBar currBar = null;
	
	boolean shutdown = false;
	public TradingStationInterface(List<Indicator> indicators)
	{
		this.indicators = indicators;
		ga = new GAEngine(indicators);
	}
	
	public Message readMessageFromSocket()
	{
		Message message = new Message();
		int len = 0;
		byte[] byteArray = new byte[4096];
		
		try {
			log.trace("read msg from inputStream");
			Arrays.fill(byteArray, (byte)0);
			input.read(byteArray, 0, 1);
			message.setTopic(Character.toString((char) byteArray[0]));
			log.trace("Message topic is '" + message.getTopic() + "'");
			
			input.read(byteArray, 0, Long.BYTES);
			message.setTimestamp(ByteBuffer.wrap(byteArray).getLong());
			log.trace("Message timestamp is '" + message.getTimestamp() + "'");

			Arrays.fill(byteArray, (byte)0);
			input.read(byteArray, 0, 4);
			len = ByteBuffer.wrap(byteArray).getInt();
			log.trace("Going to receive " + len + " bytes");

			Arrays.fill(byteArray, (byte)0);
			input.read(byteArray, 0, len);
			String jsonString = new String(byteArray, 0, len, StandardCharsets.UTF_8);
			log.trace("Received json object '" + jsonString + "'");
			
			TradeMessage trade;
			MarketBar bar;
			switch(message.getTopic())
			{
			case "A":
				trade = (TradeMessage) JSONWrapper.MAPPER.readValue(jsonString, TradeMessage.class);
				message = trade;
				break;
				
			case "B":
				bar = (MarketBar) JSONWrapper.MAPPER.readValue(jsonString, MarketBar.class);
				message = bar;
				break;
			}
			log.trace("json received converted into JAVA Object");
		}
		catch(Exception e)
		{
			log.error("Exception raised ", e);
			log.error("Check the socket status to close our side and return null", e);
			try {
				if (!socket.isClosed()) socket.close();
			} 
			catch (IOException e1) {
				;
			}
			return null;
		}
        return message;
	}
	
	public void shutdown()
	{
		shutdown = true;
	}
	
	public void closeSocket()
	{
		try {
			socket.close();
		} 
		catch (IOException e) {
			log.error("Errore closing socket", e);
		}
	}

    public TradingStationInterface() {
    	;
    }
    
    private void handleIncomingMessages()
    {
		Message message = null;
		TradeMessage trade;

		while (!shutdown)
		{
			try {
				if (input.available() > 0) {
					message = this.readMessageFromSocket();
				}
				else
				{
					message = null;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (message == null) 
			{
				Utilities.pause(100);
				continue;
			}
//			ZonedDateTime z = ((Message) message).getTimestamp()
//								.toInstant().atZone(ZoneId.systemDefault());
			log.trace("Received message: topic '" + message.getTopic() + "', " +
					  "timestamp: " + message.getTimestamp());
			switch(((Message) message).getTopic())
			{
			case "B":
				log.trace("The message is a market bar.");
				currBar = (MarketBar) message;
				log.debug("New bar received: Open " + currBar.getOpen() + 
						  " High " + currBar.getHigh() + 
						  " Low " + currBar.getLow() + 
						  " Close " + currBar.getClose());
				log.trace("updating indicators");
				for(Indicator indicator : indicators)
				{
					indicator.add(currBar);
					indicator.normalizeAndStore(currBar, (ATR) indicators.get(0), props);
					log.trace("Indicator: " + indicator.getClass().getSimpleName() + 
							  ", normalizedValue : " + indicator.getNormalizedValue());

				}
				if (prevBar != null)
				{
					Double mktMovePercent = 1000 * 
											((currBar.getClose() - prevBar.getClose()) / 
											 prevBar.getClose());
					int marketMove = mktMovePercent.intValue();
					if (marketMove > 50)
						marketMove = 50;
					else if (marketMove < -50)
						marketMove = -50;
					log.debug("mktMovePercent: " + mktMovePercent + ", marketMove " + marketMove);
					
					for(Gene g : ga.getPopulation())
					{
						String geneIndicators = "";
						int prediction = 0;
						for(int i : g.indicatorsIndex)
						{
							prediction += indicators.get(i).getNormalizedValue();
							geneIndicators += indicators.get(i).getName() + " ";
						}
						g.addScore(marketMove, (int) (prediction / props.getGeneSize()));
						log.debug("Gene indicators: " + geneIndicators + 
								  ", prediction: " + prediction +
								  ", score: " + g.score); 
					}
				}
				prevBar = currBar;
//				ga.evolve();
				barNumber++;
				break;
				
			case "T":
				log.trace("The message is a trade");
				// add the trade to the trades series
				trade = (TradeMessage) message;
				trade.setTimestamp(0);
				break;
			}       
		}

    }
    
    public void run()
    {
        try {
			socket = new Socket(props.getHost(), props.getPort());
			input = socket.getInputStream();
			log.trace("Connected to server, going to receive messages");
			handleIncomingMessages();
        } 
        catch (UnknownHostException ex) {
            log.error("Server not found: " + ex.getMessage());
            System.exit(-1);
        } 
        catch (IOException ex) {
        	log.error("I/O error: " + ex.getMessage());
            System.exit(-1);
        }
        closeSocket();
    }
}
