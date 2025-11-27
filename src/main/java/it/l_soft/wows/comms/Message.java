package it.l_soft.wows.comms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Message {
	final Logger log = LoggerFactory.getLogger(this.getClass());
    private String topic;
    protected	long timestamp;
    
    public Message() {}
    
    public Message(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    public long getTimestamp() {
    	return timestamp;
    }
    
	public void setTopic(String topic) {
		this.topic = topic;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
}