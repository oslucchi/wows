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
    String dir = "";
    String pre = "";
    String extension = "";
    String ts  = new SimpleDateFormat("MMdd_HHmmss").format(new Date());
    
    public TextFileHandler(String filePath, String preamble, String extension) 
    		throws Exception 
    {
    	
    	this.dir = filePath;
    	this.pre = preamble;
    	this.extension = extension;
    	open(true, true);
    }
    
    public TextFileHandler(String filePath, String preamble, String extension, boolean useTimestamp) 
    		throws Exception 
    {
    	this.dir = filePath;
    	this.pre = preamble;
    	this.extension = extension;
    	open(useTimestamp, true);
    }  
    
    public TextFileHandler(String filePath, String preamble, String extension, boolean useTimestamp, boolean append) 
    		throws Exception 
    {
    	this.dir = filePath;
    	this.pre = preamble;
    	this.extension = extension;
    	open(useTimestamp, append);
    }
    
    public void open(boolean useTimestamp, boolean append) throws Exception {
        try {
            file = new File(dir, pre + (useTimestamp ? "_" + ts : "") + "." + extension);
            file.getParentFile().mkdirs();
            fileBufWriter = new BufferedWriter(new FileWriter(file, append));
            log.trace("File opened at: " + file.getAbsolutePath());
        } 
        catch (Exception e) {
            log.error("Unable to open file for writing", e);
            fileBufWriter = null;
            throw e;
        }
    }
    
    public boolean canWrite() {
    	return file.canWrite();
    }
    
    public void close() {
        try {
            if (fileBufWriter != null) {
                fileBufWriter.flush();
                fileBufWriter.close();
                log.trace("CSV closed: " + (file != null ? file.getAbsolutePath() : ""));
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
