package it.l_soft.wows.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

public class TextFileHandler {
	private final Logger log = Logger.getLogger(this.getClass());

    private File file;
    private BufferedWriter fileBufWriter;

    public TextFileHandler(String filePath, String preamble, String extension) 
    		throws Exception 
    {
    	
        try {
            String dir = filePath;
            String pre = preamble;
            String ts  = new SimpleDateFormat("MMdd_HHmmss").format(new Date());
            file = new File(dir, pre + "_" + ts + "." + extension);
            file.getParentFile().mkdirs();
            fileBufWriter = new BufferedWriter(new FileWriter(file, false));
            log.info("CSV opened at: " + file.getAbsolutePath());
        } 
        catch (Exception e) {
            log.error("Unable to open CSV for writing", e);
            fileBufWriter = null;
            throw e;
        }
    }

    public void close() {
        try {
            if (fileBufWriter != null) {
                fileBufWriter.flush();
                fileBufWriter.close();
                log.info("CSV closed: " + (file != null ? file.getAbsolutePath() : ""));
            }
        } catch (IOException e) {
            log.error("Error closing CSV", e);
        }
    }
    
    public void write(String line, boolean writeLF) 
    		throws IOException 
    {
        try {
			fileBufWriter.write(line);
	        if (writeLF) fileBufWriter.newLine();
	        fileBufWriter.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw e;
		}
    }
}
