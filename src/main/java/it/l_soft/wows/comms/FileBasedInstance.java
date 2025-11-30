package it.l_soft.wows.comms;

import java.io.*;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.ga.GAEngine;
import it.l_soft.wows.ga.World;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;
import it.l_soft.wows.utils.TextFileHandler;

public class FileBasedInstance extends RunInstance {
    private final Logger log = Logger.getLogger(this.getClass());
    ApplicationProperties props = ApplicationProperties.getInstance();
    long barNumber = 0;
    
    List<Indicator> indicators = null;
    GAEngine ga = null;
    MarketBar prevBar = null;
    MarketBar currBar = null;
    ATR atrRef = null;

    InputStream input;
    Socket socket = null;
    boolean shutdown = false;
    String fileNamePublished;
    StreamHeader header;
    
    File barsSourceFile;
    TextFileHandler statsOutput;
    int barsToRead = 0;
    
    BarsHandler bh;

    public FileBasedInstance(List<Indicator> indicators, File barsSourceFile, TextFileHandler statsOutput, int barsToRead) 
    		throws Exception 
    {
        this.barsSourceFile = barsSourceFile;
        this.statsOutput = statsOutput;
    	this.indicators = indicators;
    	this.barsToRead = barsToRead;
    	
        ga = new GAEngine(indicators);
        		
        // Resolve ATR reference safely (no index assumptions)
        for (Indicator ind : indicators) {
            if (ind instanceof ATR) {
                atrRef = (ATR) ind;
                break;
            }
        }
        if (atrRef == null) {
            log.warn("ATR not found in indicators list. Vol-normalized scoring will use raw returns.");
        }

        bh = new BarsHandler(atrRef, indicators, ga.getWorlds());
        // Prepare CSV
    }

    public FileBasedInstance() { }

    @Override
    public void shutdown() { shutdown = true; }

	public void run() {
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		long barNumber = 1;

    	try (BufferedReader br = new BufferedReader(new FileReader(barsSourceFile))) {
    	    String line;
    	    while (((line = br.readLine()) != null) &&
    	    	   (barsToRead-- >= 0)) 
    	   {
    	    	String[] tokens = line.split(";");
    	    	MarketBar bar = 
    	    			new MarketBar(barNumber++, 
    	    						  sdf.parse(tokens[0] + " " + tokens[1]).getTime(),
    	    						  Double.parseDouble(tokens[2]),
    	    						  Double.parseDouble(tokens[3]),
    	    						  Double.parseDouble(tokens[4]),
    	    						  Double.parseDouble(tokens[5]),
    	    						  Long.parseLong(tokens[6]));
    	    	bh.handleBar(bar);
    	    	barNumber = bar.getBarNumber();
        	    for(World world : ga.getWorlds())
        	    {
            	    ga.evolve(world);
        	    }
    	    	
    	    }
    	    for(World world : ga.getWorlds())
    	    {
        	    world.dumpAccumulators(barsSourceFile.getName(), statsOutput);
    	    }

    	} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MissedItemsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
    }
}
