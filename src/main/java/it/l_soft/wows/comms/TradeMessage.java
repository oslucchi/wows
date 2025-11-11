package it.l_soft.wows.comms;

public class TradeMessage extends Message {
    private char side;
    private double value, quantity;

    public TradeMessage() {
    	super("A");
    }
    
    public TradeMessage(char side, double value, double quantity) {
        super("A");
    	this.side = side;
        this.value = value;
        this.quantity = quantity;
    }
    
    // Getters
    public char getSide() { return side; }
    public double getValue() { return value; }
    public double getQuantity() { return quantity; }
}