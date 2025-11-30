package it.l_soft.wows.comms;

public interface Bar {
	long getBarNumber();
	long getTimestamp();
	double getOpen();
    double getHigh();
    double getLow();
    double getClose();
    long getVolume();
}
