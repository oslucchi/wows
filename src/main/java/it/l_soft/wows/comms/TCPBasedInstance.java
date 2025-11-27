package it.l_soft.wows.comms;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.ga.GAEngine;
import it.l_soft.wows.ga.PopulationInstance;
import it.l_soft.wows.ga.Gene.ScoreHolder;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;
import it.l_soft.wows.utils.JSONWrapper;
import it.l_soft.wows.utils.RingBuffer;
import it.l_soft.wows.utils.RingBuffer.MissedItemsException;
import it.l_soft.wows.utils.TextFileHandler;
import it.l_soft.wows.utils.Utilities;

public class TCPBasedInstance extends RunInstance {
    private final Logger log = Logger.getLogger(this.getClass());
    ApplicationProperties props = ApplicationProperties.getInstance();
    long barNumber = 0;

    InputStream input;
    Socket socket = null;
    List<Indicator> indicators;
    GAEngine ga;
    RingBuffer<MarketBar> barSeries = new RingBuffer<MarketBar>(props.getBarsInMemory());
    RingBuffer<MarketBar>.ConsumerHandle barsReader = barSeries.createConsumer();
    MarketBar prevBar = null;
    MarketBar currBar = null;
    ATR atrRef = null;
    TextFileHandler csv;
    boolean shutdown = false;
    String fileNamePublished;
    StreamHeader header;
    
    File statsOutput;

    public TCPBasedInstance(List<Indicator> indicators, File statsOutput) 
    		throws Exception 
   {
        this.indicators = indicators;
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
			csv = new TextFileHandler(props.getCSVFilePath(), props.getCSVPreamble(), "csv");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // Write CSV header lazily (we need GA population for columns)
        writeCsvHeaderIfNeeded(props.getVolNormK());
    }

    public TCPBasedInstance() { }

    @Override
    public void shutdown() { shutdown = true; }

    public void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.error("Error closing socket", e);
        }
        csv.close();
    }
    
    private int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int read = in.read(buf, off + total, len - total);
            if (read == -1) {
                // EOF while still expecting bytes
                return total == 0 ? -1 : total;
            }
            total += read;
        }
        return total;
    }

    public Message readMessageFromSocket() {
        Message message = new Message();
        int len;
        byte[] byteArray = new byte[4096];

        try {
            log.trace("read msg from inputStream");

            // 1) Read topic (1 byte)
            Arrays.fill(byteArray, (byte) 0);
            int read = readFully(input, byteArray, 0, 1);
            if (read == -1) {
                // Remote side closed cleanly before a new message
                log.info("Socket closed by remote peer while waiting for topic.");
                closeSocketQuietly();
                return null;
            } else if (read < 1) {
                // EOF in the middle of a field – protocol error
                throw new EOFException("EOF while reading topic");
            }

            message.setTopic(Character.toString((char) byteArray[0]));
            log.trace("Message topic is '" + message.getTopic() + "'");

            // 2) Read timestamp (8 bytes)
            Arrays.fill(byteArray, (byte) 0);
            read = readFully(input, byteArray, 0, Long.BYTES);
            if (read == -1) {
                log.info("Socket closed by remote peer while reading timestamp (no bytes read).");
                closeSocketQuietly();
                return null;
            } else if (read < Long.BYTES) {
                throw new EOFException("EOF while reading timestamp");
            }

            message.setTimestamp(ByteBuffer.wrap(byteArray).getLong());
            log.trace("Message timestamp is '" + message.getTimestamp() + "'");

            // 3) Read payload length (4 bytes)
            Arrays.fill(byteArray, (byte) 0);
            read = readFully(input, byteArray, 0, Integer.BYTES);
            if (read == -1) {
                log.info("Socket closed by remote peer while reading payload length (no bytes read).");
                closeSocketQuietly();
                return null;
            } else if (read < Integer.BYTES) {
                throw new EOFException("EOF while reading payload length");
            }

            len = ByteBuffer.wrap(byteArray).getInt();
            log.trace("Going to receive " + len + " bytes");

            if (len < 0 || len > byteArray.length) {
                throw new IllegalArgumentException("Invalid payload length: " + len);
            }

            // 4) Read payload (len bytes)
            Arrays.fill(byteArray, (byte) 0);
            read = readFully(input, byteArray, 0, len);
            if (read == -1) {
                log.info("Socket closed by remote peer while reading payload (no bytes read).");
                closeSocketQuietly();
                return null;
            } else if (read < len) {
                throw new EOFException("EOF while reading payload: expected " + len + ", got " + read);
            }

            String jsonString = new String(byteArray, 0, len, StandardCharsets.UTF_8);
            log.trace("Received json object '" + jsonString + "'");

            switch (message.getTopic()) {
                case "A":
                    message = JSONWrapper.MAPPER.readValue(jsonString, TradeMessage.class);
                    break;
                case "B":
                    message = JSONWrapper.MAPPER.readValue(jsonString, MarketBar.class);
                    break;
                case "H":
                    message = JSONWrapper.MAPPER.readValue(jsonString, StreamHeader.class);
                    break;
                default:
                    log.warn("Unknown topic '" + message.getTopic() + "'");
                    break;
            }

            log.trace("json received converted into JAVA Object");
            return message;

        } catch (Exception e) {
            log.error("Exception raised while reading from socket", e);
            log.error("Check the socket status to close our side and return null");
            closeSocketQuietly();
            return null;
        }
    }

    private void closeSocketQuietly() {
        try {
            if (socket != null && !socket.isClosed()) {
            	for(int i = 0; i < props.getNumberOfPopulations(); i++)
            	{
                	PopulationInstance instance = ga.getPopulation(i);

            		long[] acc = instance.getAccumulators();
            		System.out.println(String.format("%s,%d,%d,%d,%.4f,%d,%.4f,$d,$.4f", 
            										 header.dataFileName, props.getHorizonBars(i),
            										 acc[GAEngine.TOTAL_RECORDS],
            										 acc[GAEngine.MATCHES],
            										 acc[GAEngine.MATCHES] / acc[GAEngine.TOTAL_RECORDS],
            										 acc[GAEngine.FLAT],
            										 acc[GAEngine.FLAT] / acc[GAEngine.MATCHES],
            										 acc[GAEngine.ERRORS],
            										 acc[GAEngine.ERRORS] / acc[GAEngine.MATCHES]));    										 
            	}
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void handleIncomingMessages() 
    		throws MissedItemsException, IOException 
    {
        Message message = null;
        TradeMessage trade;

        while (!shutdown) {
            try {
                if (input.available() > 0) {
                    message = this.readMessageFromSocket();
                } else {
                    message = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (message == null) {
                Utilities.pause(100);
                continue;
            }

            log.trace("Received message: topic '" + message.getTopic() + "', timestamp: " + message.getTimestamp());
            switch(message.getTopic()) {
                case "B":
                    log.trace("The message is a market bar.");
                    currBar = (MarketBar) message;
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
                    	break;
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
                    break;

                case "T":
                    log.trace("The message is a trade");
                    trade = (TradeMessage) message;
                    trade.setTimestamp(0);
                    break;

                case "H":
                    log.trace("The message is a header.");
                    header = (StreamHeader) message;
                    
            }
        }
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
    			if ((prevBar.getBarNumber() > 0) && 
    				(instance.getArbitrator().getReader().getContentAsList().size() > 0))
    			{
    				prediction = (ScoreHolder) instance.getArbitrator().getReader().getContentAsList().getLast();
                    sb.append(',').append(prediction.name);
                    sb.append(',').append(fmt(prediction.timestamp));
                    sb.append(',').append(fmt(prediction.predictedMarketPrice));
                    sb.append(',').append(fmt(prediction.direction));
    			}
    			else
    			{
    				sb.append(",,0,0,0");
    			}
                sb.append(',').append(fmt(instance.getArbitrator().getWinAccumulator()));
                sb.append(',').append(fmt(instance.getArbitrator().getLongWin()));
                sb.append(',').append(fmt(instance.getArbitrator().getTotalLong()));
                sb.append(',').append(fmt(instance.getArbitrator().getShortWin()));
                sb.append(',').append(fmt(instance.getArbitrator().getTotalShort()));
                sb.append(',').append(fmt(instance.getArbitrator().getTotalScore()));

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
        try {
            socket = new Socket(props.getHost(), props.getPort());
            input = socket.getInputStream();
            log.trace("Connected to server, going to receive messages");
            handleIncomingMessages();
        } catch (UnknownHostException ex) {
            log.error("Server not found: " + ex.getMessage());
            System.exit(-1);
        } catch (IOException ex) {
            log.error("I/O error: " + ex.getMessage());
            System.exit(-1);
        } catch (MissedItemsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    ga.evalPopulationAccumulators("TCPInterface", currBar.getBarNumber(), statsOutput);
        closeSocket();
    }
}
