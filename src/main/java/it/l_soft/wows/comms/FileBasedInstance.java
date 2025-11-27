package it.l_soft.wows.comms;

import java.io.*;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.ga.GAEngine;
import it.l_soft.wows.ga.Gene.ScoreHolder;
import it.l_soft.wows.ga.PopulationInstance;
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
    TextFileHandler csv;
    boolean shutdown = false;
    String fileNamePublished;
    StreamHeader header;
    
    File barsSourceFile;
    File statsOutput;
    int barsToRead = 0;

    public FileBasedInstance(List<Indicator> indicators, File barsSourceFile, File statsOutput, int barsToRead) {
        this.barsSourceFile = barsSourceFile;
        this.statsOutput = statsOutput;
    	this.indicators = indicators;
    	this.barsToRead = barsToRead;
    	
        ga = new GAEngine(indicators);

        // Resolve ATR reference safely (no index assumptions)
        for (Indicator ind : indicators) {
            if (ind instanceof it.l_soft.wows.indicators.volatility.ATR) {
                atrRef = (it.l_soft.wows.indicators.volatility.ATR) ind;
                break;
            }
        }
        if (atrRef == null) {
            log.warn("ATR not found in indicators list. Vol-normalized scoring will use raw returns.");
        }

        // Prepare CSV
        try {
			csv = new TextFileHandler(props.getCSVFilePath(), 
									  barsSourceFile.getName() + "_out", "csv");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // Write CSV header lazily (we need GA population for columns)
        writeCsvHeaderIfNeeded(props.getVolNormK());
    }

    public FileBasedInstance() { }

    @Override
    public void shutdown() { shutdown = true; }
    
    
    private void handleNextBar(MarketBar bar) 
    		throws MissedItemsException {

        currBar = bar;
        currBar.setBarNumber(barNumber);
		prevBar = null;
        try {
			if (barSeries.getLength() > 0)
				prevBar = barsReader.poll().getValue();
		} 
        catch (MissedItemsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        log.debug("New bar received: BarNumber " + barNumber +
                  " Close " + currBar.getClose() +
                  " Prev " + (prevBar != null ? prevBar.getClose() : "N/A"));
        
        barSeries.publish(currBar);
        
        // --- 1) Update indicators (ensure ATR is updated first for scaling) ---
        if (atrRef != null) {
            atrRef.add(currBar);
            atrRef.normalizeAndStore(currBar, atrRef, props);
        }
        for (Indicator indicator : indicators) {
            if (indicator == atrRef) continue;
            indicator.add(currBar);
            indicator.normalizeAndStore(currBar, atrRef, props);
            log.trace("Indicator: " + indicator.getClass().getSimpleName() +
                      ", normalizedValue: " + indicator.getNormalizedValue());
        }
        
        if (currBar.getBarNumber() == 0)
        {
        	barNumber++;
        	// nothing could be done on the very first bar
        	return;
        }
        if (currBar.getBarNumber() < props.getGenesWarmUpBars())
        {
        	barNumber++;
        	// do not consider this yet
        	return;
        }
        
        // -- 2) calculate normalized values for the market move
        double ret = (currBar.getClose() - prevBar.getClose()) / prevBar.getClose();
        double atrAbs = (atrRef != null) ? atrRef.value() : Double.NaN;
        double atrPct = (!Double.isNaN(atrAbs) && prevBar.getClose() != 0.0) ? (atrAbs / prevBar.getClose()) : 0.0;
        double denom = Math.max(1e-9, props.getVolNormK() * Math.max(1e-9, atrPct));
        double marketMoveNorm = Math.max(-1.0, Math.min(1.0, ret / denom));                    
        
        // --- 3) For each gene: build composite yhat in [-1,1] and eval prediction
        ga.evalPopulations(indicators, currBar, prevBar, denom);
                                
        // --- 5) Append one CSV line for this bar ---
        if (prevBar != null)
        {
            appendCsvLine(barNumber, prevBar, ret, atrAbs, atrPct, props.getVolNormK(), marketMoveNorm);
        }
        ga.evolve(); // re-enable when you decide the cadence
        barNumber++;
    }
    
    private void writeCsvHeaderIfNeeded(double K_VOL) {
        if (csv == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            // Core bar fields
            sb.append("barNumber,timestamp,open,high,low,close,ret,atrAbs,atrPct,K_VOL,moveNorm");
            // For each gene, add columns
            sb.append(",").append("name");
            sb.append(",").append("ts");
            sb.append(",").append("predicted");
            sb.append(",").append("dir");
            sb.append(",").append("#win");
            sb.append(",").append("#Lwin");
            sb.append(",").append("TLong");
            sb.append(",").append("#Swin");
            sb.append(",").append("TShort");
            sb.append(",").append("score");
            csv.write(sb.toString(), true);
        } 
        catch (Exception e) {
            log.error("Error writing CSV header", e);
        }
    }

    private void appendCsvLine(long barNo, MarketBar bar, double ret, double atrAbs,
                               double atrPct, double K_VOL, double moveNorm) {
        if (csv == null) return;
        for (int i = 0; i < props.getNumberOfPopulations(); i++)
        {
        	PopulationInstance instance = ga.getPopulation(i);
            try {
                StringBuilder sb = new StringBuilder(1024);
                // Base bar fields
                sb.append(barNo).append(',')
                  .append(bar.getTimestamp()).append(',')
                  .append(fmt(bar.getOpen())).append(',')
                  .append(fmt(bar.getHigh())).append(',')
                  .append(fmt(bar.getLow())).append(',')
                  .append(fmt(bar.getClose())).append(',')
                  .append(fmt(ret)).append(',')
                  .append(fmt(atrAbs)).append(',')
                  .append(fmt(atrPct)).append(',')
                  .append(fmt(K_VOL)).append(',')
                  .append(fmt(moveNorm));

    	    	ScoreHolder prediction;
    			prediction = null;
    			if (prevBar.getBarNumber() > 0)
    			{
    				if ((instance.getArbitrator() != null) &&
    					(instance.getArbitrator().getReader().getContentAsList().size() > 0))
    				{
	    				prediction = (ScoreHolder) instance.getArbitrator().getReader().getContentAsList().getLast();
	                    sb.append(',').append(prediction.name);
	                    sb.append(',').append(fmt(prediction.timestamp));
	                    sb.append(',').append(fmt(prediction.predictedMarketPrice));
	                    sb.append(',').append(fmt(prediction.direction));
	                    sb.append(',').append(fmt(instance.getArbitrator().getWinAccumulator()));
	                    sb.append(',').append(fmt(instance.getArbitrator().getLongWin()));
	                    sb.append(',').append(fmt(instance.getArbitrator().getTotalLong()));
	                    sb.append(',').append(fmt(instance.getArbitrator().getShortWin()));
	                    sb.append(',').append(fmt(instance.getArbitrator().getTotalShort()));
	                    sb.append(',').append(fmt(instance.getArbitrator().getTotalScore()));
	    			}
	    			else
	    			{
	    				sb.append(",,0,0,0,0,0,0,0,0,0");
	    			}
    			}
    			else
    			{
    				sb.append(",,0,0,0,0,0,0,0,0,0");
    			}
    					

                csv.write(sb.toString(), true);
            } 
            catch (IOException e) {
                log.error("Error writing CSV line", e);
            }
        }
    }

    private static String fmt(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "";
        // compact, enough for plotting
        return String.format(java.util.Locale.US, "%.6f", d);
    }

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
    	    	handleNextBar(bar);
    	    }
    	    ga.evalPopulationAccumulators(barsSourceFile.getName(), currBar.getBarNumber(), statsOutput);

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
		}    	
    }
}
