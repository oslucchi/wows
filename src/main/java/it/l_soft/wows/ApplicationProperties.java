package it.l_soft.wows;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApplicationProperties {
	private static ApplicationProperties instance = null;
	final Logger log = LoggerFactory.getLogger(this.getClass());
	
	private static String propertiesPath = null;

	private int port;
	private String host;
	private long intraMessagePause;
	private String CSVFilePath;
	private String CSVPreamble;
	private boolean consoleOut;
	private long shutdownGracePeriod;
	private String indicatorsToInstantiate;
	
//	private int SMACrossing_ShortWindow = 50;
//	private int SMACrossing_LongWindow = 200;
//	
//    private int RSIMACD_RSIPeriod = 14;
//    private int RSIMACD_MACDShortPeriod = 12;
//    private int RSIMACD_MACDLongPeriod = 26;
//    private int RSIMACD_MACDSignalPeriod = 9;
//    
//    private int BollingerVolume_Period = 20;
//    private double BollingerVolume_Multiplier = 2.0;
//    
//    private double ParabolicSARADX_InitialSar; // Ultimo valore Close della candela precedente
//    private double ParabolicSARADX_initialEp; // Ultimo valore High (se in trend rialzista) o Low (se in trend ribassista) della candela precedente
//    private double ParabolicSARADX_Step = 0.02;
//    private double ParabolicSARADX_MaxStep = 0.2;
//    private int ParabolicSARADX_ADXPeriod = 14;
//    
//    private int FibonacciCCI_CCIPeriod = 20;
//    private double[] FibonacciCCI_Levels = {0.382, 0.5, 0.618};
//    
//    private int MACDRSI_RsiPeriod= 14;
//    private int MACDRSI_MACDShortPeriod = 12;
//    private int MACDRSI_MACDLongPeriod = 26;
//    private int MACDRSI_MACDSignalPeriod = 9;
//
//    private int KeltnerATR_KeltnerPeriod = 20;
//    private int KeltnerATR_ATRPeriod = 14;
//    private double KeltnerATR_Multiplier = 2.0;
//
//    private int IchimokuCloud_TenkanPeriod = 9;
//    private int IchimokuCloud_KijunPeriod = 26;
//    private int IchimokuCloud_SenkouSpanBPeriod = 52;
//
//    private int DMIADX_ADXPeriod = 14;
//    private int DMIADX_DMIPeriod = 14;
//    private int DMIADX_CrossUpBareer = 25;
//    private int DMIADX_CrossDownBareer = 20;
//
//    private int StochasticRSI_StochasticPeriod = 14;
//    private int StochasticRSI_RSIPeriod = 14;
    
    private String[] strategiesToUse;
	
    		
    public static ApplicationProperties getInstance(String propPath)
	{
		if (propPath != null)
		{
			propertiesPath = propPath;
		}

		if (instance == null)
		{
			instance = new ApplicationProperties();
		}
		return(instance);
	}
	
	public static ApplicationProperties getInstance()
	{
		if (instance == null)
		{
			instance = new ApplicationProperties();
		}
		return(instance);
	}
	
	private ApplicationProperties()
	{
		String variable = "";
		String[] values;

		log.trace("ApplicationProperties start");
		Properties properties = new Properties();
		
    	try 
    	{
        	InputStream in;
        	if (propertiesPath == null)
        	{
        		in = ApplicationProperties.class.getResourceAsStream("/package.properties");
        	}
        	else
        	{
        		String confFilePath = System.getProperty("user.dir") + File.separator + propertiesPath;
        		
        	    File initialFile = new File(confFilePath);
        	    System.out.println("ApplicationPropertes using '" + confFilePath + "'");

        	    in = new FileInputStream(initialFile);
        	}
        	if (in == null)
        	{
        		log.error("resource path not found");
        		return;
        	}
        	properties.load(in);
	    	in.close();
    	    System.out.println("ApplicationPropertes: " + properties.size() + " properties loaded");
		}
    	catch(IOException e) 
    	{
			log.warn("Exception " + e.getMessage(), e);
    		return;
		}
    	
		try
    	{

			variable = "port";
			port = 12345;
	        if (properties.getProperty(variable) != null)
	        {
	        	port = Integer.parseInt(properties.getProperty(variable).trim());
	        }

			variable = "host";
			host = "";
	        if (properties.getProperty(variable) != null)
	        {
	        	host = properties.getProperty(variable).trim();
	        }

			variable = "intraMessagePause";
			intraMessagePause = 1000;
	        if (properties.getProperty(variable) != null)
	        {
	        	intraMessagePause = Long.parseLong(properties.getProperty(variable).trim());
	        }
	        
			variable = "CSVFilePath";
			CSVFilePath = "./output";
	        if (properties.getProperty(variable) != null)
	        {
	        	CSVFilePath = properties.getProperty(variable).trim();
	        }

			variable = "CSVPreamble";
			CSVPreamble = "wows_";
	        if (properties.getProperty(variable) != null)
	        {
	        	CSVPreamble = properties.getProperty(variable).trim();
	        }
	        
	        variable = "consoleOut";
	        if (properties.getProperty(variable) != null)
	        {
	        	consoleOut = Boolean.parseBoolean(properties.getProperty(variable).trim());
	        }

	        variable = "shutdownGracePeriod";
	        if (properties.getProperty(variable) != null)
	        {
	        	shutdownGracePeriod = Long.parseLong(properties.getProperty(variable).trim());
	        }

	        variable = "indicatorsToInstantiate";
	        if (properties.getProperty(variable) != null)
	        {
	        	indicatorsToInstantiate = properties.getProperty(variable).trim();
	        }	        
//	        variable = "SMACrossing_ShortWindow";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	SMACrossing_ShortWindow = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "SMACrossing_LongWindow";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	SMACrossing_LongWindow = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "RSIMACD_RSIPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	RSIMACD_RSIPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "RSIMACD_MACDShortPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	RSIMACD_MACDShortPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "RSIMACD_MACDLongPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	RSIMACD_MACDLongPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "RSIMACD_MACDSignalPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	RSIMACD_MACDSignalPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "BollingerVolume_Period";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	BollingerVolume_Period = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "BollingerVolume_Multiplier";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	BollingerVolume_Multiplier = Double.parseDouble(properties.getProperty(variable).trim());
//	        }
//	        
//	        variable = "ParabolicSARADX_InitialSar";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	ParabolicSARADX_InitialSar = Double.parseDouble(properties.getProperty(variable).trim());
//	        }
//	        variable = "ParabolicSARADX_initialEp";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	ParabolicSARADX_initialEp = Double.parseDouble(properties.getProperty(variable).trim());
//	        }
//	        variable = "ParabolicSARADX_Step";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	ParabolicSARADX_Step = Double.parseDouble(properties.getProperty(variable).trim());
//	        }
//	        variable = "ParabolicSARADX_MaxStep";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	ParabolicSARADX_MaxStep = Double.parseDouble(properties.getProperty(variable).trim());
//	        }
//	        variable = "ParabolicSARADX_ADXPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	ParabolicSARADX_ADXPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "FibonacciCCI_CCIPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	FibonacciCCI_CCIPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        variable = "FibonacciCCI_Levels";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	values = properties.getProperty(variable).split(",");
//	        	int idx = 0;
//	        	FibonacciCCI_Levels = new double[values.length];
//	        	for(String value : values)
//	        	{
//	        		FibonacciCCI_Levels[idx++] = Double.parseDouble(value.trim());
//	        	}
//	        }
//	                
//	        variable = "MACDRSI_RsiPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	MACDRSI_RsiPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        
//
//    		variable = "MACDRSI_MACDShortPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	MACDRSI_MACDShortPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "MACDRSI_MACDLongPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	MACDRSI_MACDLongPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "MACDRSI_MACDSignalPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	MACDRSI_MACDSignalPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "KeltnerATR_KeltnerPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	KeltnerATR_KeltnerPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "KeltnerATR_ATRPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	KeltnerATR_ATRPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "KeltnerATR_Multiplier";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	KeltnerATR_Multiplier = Double.parseDouble(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "IchimokuCloud_TenkanPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	IchimokuCloud_TenkanPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "IchimokuCloud_KijunPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	IchimokuCloud_KijunPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "IchimokuCloud_SenkouSpanBPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	IchimokuCloud_SenkouSpanBPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "DMIADX_ADXPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	DMIADX_ADXPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "DMIADX_DMIPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	DMIADX_DMIPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "DMIADX_CrossUpBareer";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	DMIADX_CrossUpBareer = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//	        
//	        variable = "DMIADX_CrossDownBareer";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	DMIADX_CrossDownBareer = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "StochasticRSI_StochasticPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	StochasticRSI_StochasticPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
//
//	        variable = "StochasticRSI_RSIPeriod";
//	        if (properties.getProperty(variable) != null)
//	        {
//	        	StochasticRSI_RSIPeriod = Integer.parseInt(properties.getProperty(variable).trim());
//	        }
	        
	        variable = "strategiesToUse";
	        if (properties.getProperty(variable) != null)
	        {
	        	values = properties.getProperty(variable).split(",");
	        	int idx = 0;
	        	strategiesToUse = new String[values.length];
	        	for(String value : values)
	        	{
	        		strategiesToUse[idx++] = value.trim();
	        	}
	        }
	        
    	}
    	catch(NumberFormatException e)
    	{
    		log.error("The format for the variable '" + variable + "' is incorrect (" +
    					 properties.getProperty("sessionExpireTime") + ")", e);
    		System.out.println("The format for the variable '" + variable + "' is incorrect (" +
					 properties.getProperty("sessionExpireTime") + ")");
    		System.exit(-1);
    	}
	}

	public boolean getConsoleOut() {
		return consoleOut;
	}

	public int getPort() {
		return port;
	}

	public String getHost() {
		return host;
	}

	public long getIntraMessagePause() {
		return intraMessagePause;
	}
	
	public String[] getStrategiesToUse() {
		return strategiesToUse;
	}

	public String getStrategiesToUse(int i) {
		return strategiesToUse[i];
	}

	public String getCSVFilePath() {
		return CSVFilePath;
	}

	public String getCSVPreamble() {
		return CSVPreamble;
	}

	public long getShutdownGracePeriod() {
		return shutdownGracePeriod;
	}

	public String getIndicatorsToInstantiate() {
		return indicatorsToInstantiate;
	}
	
//	public int getSMACrossing_ShortWindow() {
//		return SMACrossing_ShortWindow;
//	}
//
//	public int getSMACrossing_LongWindow() {
//		return SMACrossing_LongWindow;
//	}
//
//	public int getRSIMACD_RSIPeriod() {
//		return RSIMACD_RSIPeriod;
//	}
//
//	public int getRSIMACD_MACDShortPeriod() {
//		return RSIMACD_MACDShortPeriod;
//	}
//
//	public int getRSIMACD_MACDLongPeriod() {
//		return RSIMACD_MACDLongPeriod;
//	}
//
//	public int getRSIMACD_MACDSignalPeriod() {
//		return RSIMACD_MACDSignalPeriod;
//	}
//
//	public int getBollingerVolume_Period() {
//		return BollingerVolume_Period;
//	}
//
//	public double getBollingerVolume_Multiplier() {
//		return BollingerVolume_Multiplier;
//	}
//
//	public double getParabolicSARADX_InitialSar() {
//		return ParabolicSARADX_InitialSar;
//	}
//
//	public double getParabolicSARADX_initialEp() {
//		return ParabolicSARADX_initialEp;
//	}
//
//	public double getParabolicSARADX_Step() {
//		return ParabolicSARADX_Step;
//	}
//
//	public double getParabolicSARADX_MaxStep() {
//		return ParabolicSARADX_MaxStep;
//	}
//
//	public int getParabolicSARADX_ADXPeriod() {
//		return ParabolicSARADX_ADXPeriod;
//	}
//
//	public int getFibonacciCCI_CCIPeriod() {
//		return FibonacciCCI_CCIPeriod;
//	}
//
//	public double[] getFibonacciCCI_Levels() {
//		return FibonacciCCI_Levels;
//	}
//
//	public double getFibonacciCCI_Levels(int i) {
//		return FibonacciCCI_Levels[i];
//	}
//
//	public int getMACDRSI_RsiPeriod() {
//		return MACDRSI_RsiPeriod;
//	}
//
//	public int getMACDRSI_MACDShortPeriod() {
//		return MACDRSI_MACDShortPeriod;
//	}
//
//	public int getMACDRSI_MACDLongPeriod() {
//		return MACDRSI_MACDLongPeriod;
//	}
//
//	public int getMACDRSI_MACDSignalPeriod() {
//		return MACDRSI_MACDSignalPeriod;
//	}
//
//	public int getKeltnerATR_KeltnerPeriod() {
//		return KeltnerATR_KeltnerPeriod;
//	}
//
//	public int getKeltnerATR_ATRPeriod() {
//		return KeltnerATR_ATRPeriod;
//	}
//
//	public double getKeltnerATR_Multiplier() {
//		return KeltnerATR_Multiplier;
//	}
//
//	public int getIchimokuCloud_TenkanPeriod() {
//		return IchimokuCloud_TenkanPeriod;
//	}
//
//	public int getIchimokuCloud_KijunPeriod() {
//		return IchimokuCloud_KijunPeriod;
//	}
//
//	public int getIchimokuCloud_SenkouSpanBPeriod() {
//		return IchimokuCloud_SenkouSpanBPeriod;
//	}
//
//	public int getDMIADX_ADXPeriod() {
//		return DMIADX_ADXPeriod;
//	}
//
//	public int getDMIADX_DMIPeriod() {
//		return DMIADX_DMIPeriod;
//	}
//
//	public int getDMIADX_CrossUpBareer() {
//		return DMIADX_CrossUpBareer;
//	}
//
//	public int getDMIADX_CrossDownBareer() {
//		return DMIADX_CrossDownBareer;
//	}
//
//	public int getStochasticRSI_StochasticPeriod() {
//		return StochasticRSI_StochasticPeriod;
//	}
//
//	public int getStochasticRSI_RSIPeriod() {
//		return StochasticRSI_RSIPeriod;
//	}
//
}
