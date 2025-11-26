package it.l_soft.wows.comms;

public class StreamHeader extends Message {
	String dataFileName;
	
	public StreamHeader()
	{
		super("H");
	}
	public StreamHeader(String dataFileName)
	{
		super("H");
		this.dataFileName = dataFileName;
	}
	
	public void setDataFileName(String dataFileName)
	{
		this.dataFileName = dataFileName;
	}
}
