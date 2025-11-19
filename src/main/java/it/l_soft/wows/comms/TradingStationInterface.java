package it.l_soft.wows.comms;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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
    ATR atrRef = null;

    // CSV writer
    private BufferedWriter csv;
    private boolean csvHeaderWritten = false;
    private File csvFile;

    boolean shutdown = false;

    public TradingStationInterface(List<Indicator> indicators) {
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
        initCsv();
    }

    public TradingStationInterface() { }

    public void shutdown() { shutdown = true; }

    public void closeSocket() {
        try {
            if (csv != null) {
                csv.flush();
                csv.close();
                log.info("CSV closed: " + (csvFile != null ? csvFile.getAbsolutePath() : ""));
            }
        } catch (IOException e) {
            log.error("Error closing CSV", e);
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.error("Error closing socket", e);
        }
    }

    private void initCsv() {
        try {
            String dir = props.getCSVFilePath();
            String pre = props.getCSVPreamble();
            String ts  = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            csvFile = new File(dir, pre + "genes_stream_" + ts + ".csv");
            csvFile.getParentFile().mkdirs();
            csv = new BufferedWriter(new FileWriter(csvFile, false));
            log.info("CSV opened at: " + csvFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Unable to open CSV for writing", e);
            csv = null;
        }
    }

    private void writeCsvHeaderIfNeeded(double K_VOL) {
        if (csv == null || csvHeaderWritten) return;
        try {
            StringBuilder sb = new StringBuilder();
            // Core bar fields
            sb.append("barNumber,timestamp,open,high,low,close,ret,atrAbs,atrPct,K_VOL,moveNorm");
            // For each gene, add columns
            for (Gene g : ga.getPopulation()) {
                String gn = safe(g.getName());
                sb.append(",").append(gn).append("_yPrev");
                sb.append(",").append(gn).append("_yNow");
                sb.append(",").append(gn).append("_step");
                sb.append(",").append(gn).append("_total");
            }
            csv.write(sb.toString());
            csv.newLine();
            csv.flush();
            csvHeaderWritten = true;
        } catch (IOException e) {
            log.error("Error writing CSV header", e);
        }
    }

    private static String safe(String s) {
        if (s == null) return "gene";
        // replace commas and spaces to keep CSV clean
        return s.replace(",", "_").replace(" ", "_");
    }

    public Message readMessageFromSocket() {
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

            switch(message.getTopic()) {
                case "A":
                    message = (TradeMessage) JSONWrapper.MAPPER.readValue(jsonString, TradeMessage.class);
                    break;
                case "B":
                    message = (MarketBar) JSONWrapper.MAPPER.readValue(jsonString, MarketBar.class);
                    break;
            }
            log.trace("json received converted into JAVA Object");
        } catch(Exception e) {
            log.error("Exception raised ", e);
            log.error("Check the socket status to close our side and return null", e);
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
            return null;
        }
        return message;
    }

    private void handleIncomingMessages() {
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
                    log.debug("New bar received: Open " + currBar.getOpen() +
                              " High " + currBar.getHigh() +
                              " Low " + currBar.getLow() +
                              " Close " + currBar.getClose());

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

                    if (prevBar != null) {
                        // Read configurable params
                        final double TAU = props.getPredictionTemperature();
                        final double K_VOL = props.getVolNormK();

                        // --- 2) Realized market move (vol-normalized to [-1,1]) ---
                        double ret = (currBar.getClose() - prevBar.getClose()) / prevBar.getClose();
                        double atrAbs = (atrRef != null) ? atrRef.value() : Double.NaN;
                        double atrPct = (!Double.isNaN(atrAbs) && prevBar.getClose() != 0.0)
                                ? (atrAbs / prevBar.getClose())
                                : 0.0;
                        double denom = Math.max(1e-9, K_VOL * Math.max(1e-9, atrPct));
                        double marketMoveNorm = Math.max(-1.0, Math.min(1.0, ret / denom));

                        // Write CSV header lazily (we need GA population for columns)
                        writeCsvHeaderIfNeeded(K_VOL);

                        if (log.isDebugEnabled()) {
                            log.debug(String.format(
                                "[BAR %d] ts=%d close_prev=%.6f close=%.6f ret=%.6f atrAbs=%.6f atrPct=%.6f K=%.3f moveNorm=%.4f",
                                barNumber,
                                currBar.getTimestamp(),
                                prevBar.getClose(), currBar.getClose(),
                                ret, atrAbs, atrPct, K_VOL, marketMoveNorm
                            ));
                        }

                        // --- 3) For each gene: build composite yhat in [-1,1] and score ---
                        for (Gene g : ga.getPopulation()) {
                            double z = 0.0;
                            for (int i : g.getIndicatorIndices()) {
                                // each indicator normalized to [-50,50] → divide by 50 → [-1,1]
                                z += indicators.get(i).getNormalizedValue() / 50.0;
                            }
                            z /= Math.max(1, g.getIndicatorIndices().length); // average
                            double yhat = Math.tanh(z / TAU);
                            int composite50 = (int) Math.round(50.0 * yhat);
                            
                            double diff = yhat - marketMoveNorm;      // signed error
//                            double absDiff = Math.abs(diff);  // absolute error
                            double sqDiff  = diff * diff;     // squared error (for MSE)
                            g.setScore(sqDiff);
                            if (log.isTraceEnabled()) {
                                log.trace(String.format("[BAR %d][GENE %s] z=%.4f yhat=%.4f comp=%d",
                                    barNumber, g.getName(), z, yhat, composite50));
                            }
                        }

                        // --- 4) Append one CSV line for this bar ---
                        appendCsvLine(barNumber, currBar, ret, atrAbs, atrPct, K_VOL, marketMoveNorm);
                    }

                    prevBar = currBar;
                    // ga.evolve(); // re-enable when you decide the cadence
                    barNumber++;
                    break;

                case "T":
                    log.trace("The message is a trade");
                    trade = (TradeMessage) message;
                    trade.setTimestamp(0);
                    break;
            }
        }
    }

    private void appendCsvLine(long barNo, MarketBar bar, double ret, double atrAbs,
                               double atrPct, double K_VOL, double moveNorm) {
        if (csv == null) return;
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

            // Gene fields
            for (Gene g : ga.getPopulation()) {
                sb.append(',').append(fmt(g.getScore()));
            }

            csv.write(sb.toString());
            csv.newLine();
            csv.flush();
        } catch (IOException e) {
            log.error("Error writing CSV line", e);
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
        }
        closeSocket();
    }
}
