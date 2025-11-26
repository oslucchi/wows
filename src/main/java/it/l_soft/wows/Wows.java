package it.l_soft.wows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import it.l_soft.wows.comms.FileBasedInstance;
import it.l_soft.wows.comms.RunInstance;
import it.l_soft.wows.comms.TCPBasedInstance;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.IndicatorFactory;

public class Wows {
	static final Logger log = Logger.getLogger(Wows.class);
	static ApplicationProperties props = ApplicationProperties.getInstance();
	static String sourceDirPath;
	static String statsFilePath;
	static final int OPT_RUN_ON_CSV_FOLDER = 0;
	static final int OPT_STATS_FILE_PATH = 0;
	static final int OPT_NUM_OF_BARS_TO_READ = 0;
	static boolean[] options = {false,false,false};
	static String usage = String.format("usage: wows [-d csv bars folder [-h how many bars]] [-s stats file path]");
	static List<Indicator> indicators = new ArrayList<Indicator>();
	static File sourceDir = null, statsOut = null;
	static RunInstance instance = null;
	static int howManyBarsRead = -1;
	
	private static void initInlineOptions(String[] args) 
			throws IOException
	{
		for(int i = 0; i < args.length; i++)
		{
			switch(args[i])
			{
			case "-d":
				i++;
				sourceDirPath = args[i];
				sourceDir = new File(sourceDirPath);
				if (!sourceDir.isDirectory() || !sourceDir.canRead())
				{
					System.out.println("The directory " + sourceDirPath + " doesn't exists or is not accessible");
					System.out.println(usage);
					System.exit(-1);
				}
				options[OPT_RUN_ON_CSV_FOLDER] = true;
				break;

			case "-s":
				i++;
				statsFilePath = args[i];
				statsOut = new File(statsFilePath);
				statsOut.createNewFile();
				if (!statsOut.canWrite())
				{
					System.out.println("the specified path " + statsFilePath + " is not accessible or can't be written");
					System.out.println(usage);
					System.exit(-1);
				}
				options[OPT_STATS_FILE_PATH] = true;
				break;
				
			case "-h":
				i++;
				howManyBarsRead = Integer.parseInt(args[i]);
				options[OPT_NUM_OF_BARS_TO_READ] = true;
				break;
				
			default:
				System.out.println(usage);
				System.exit(-1);
			}
		}		
	}
	
	private static void initLogger()
	{
		Logger rootLogger = Logger.getLogger("main.java.it.l_soft.wows");
		rootLogger.setLevel(Level.TRACE); // Allow all loggers to inherit TRACE

		Appender appender = rootLogger.getAppender("console");
		if (appender instanceof ConsoleAppender) {
		    ((ConsoleAppender) appender).setThreshold(Level.TRACE); // Console outputs all logs
		}
		
		System.out.println("Logger level: " + log.getLevel());
		System.out.println("Effective level: " + log.getEffectiveLevel());
		System.out.println("Working directory: " + new java.io.File(".").getAbsolutePath());
	}

	private static void runNewRound(File sourceFile) 
			throws InterruptedException, IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		if (options[OPT_RUN_ON_CSV_FOLDER])
		{
			instance = new FileBasedInstance(indicators, sourceFile, statsOut, howManyBarsRead);
		}
		else 
		{
			instance = new TCPBasedInstance(indicators);
		}
		instance.run();
		
		boolean shuttingDown = false;
		long now = 0;
		log.trace("Now spawinig the child thread");
		while(instance.isAlive())
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
			    	instance.shutdown();
			    	log.trace("Wating for modules to shutdown ");
			    }
		    }
			else
			{
				if ((new Date().getTime()) - now > props.getShutdownGracePeriod())
				{
					log.warn("Shutdown grace period expired. Killing the thread");
					instance.interrupt();
				}
				else
				{
					log.warn(".");
				}
			}
		}		
	}
	
	public static void main(String[] args) throws InterruptedException, IOException {

		initLogger();
		initInlineOptions(args);
		
		// Create the list of indicators to be used based on the configuration provided 
        for(String token: props.getIndicatorsToInstantiate().split(";"))
        {
            token = token.trim();
            if (token.isEmpty()) continue;

            indicators.add(IndicatorFactory.createFromToken(token));
        }

        if (options[OPT_RUN_ON_CSV_FOLDER])
        {
        	// Perform on a list files contained in the folder passed as parameter
        	File[] files = sourceDir.listFiles();
			if (statsOut == null)
			{
				statsOut = new File(props.getCSVFilePath() + "/stats_massive.csv");
			}

        	// Print name of the all files present in that path
        	if (files != null) {
        		for (File file : files) {
        			if (!file.getName().endsWith("csv")) continue;
        			System.out.println("Running on '" + file.getName() + "'");
        			runNewRound(file);
        		}
        	}
        }
        else
        {
        	// Use a TCP publisher as source
        	runNewRound(null);
        }

		log.trace("Going to exit now");
	}
}
