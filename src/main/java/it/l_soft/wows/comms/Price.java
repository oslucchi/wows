package it.l_soft.wows.comms;

import java.util.function.ToDoubleFunction;

public enum Price {
    CLOSE(Bar::getClose),
    OPEN(Bar::getOpen),
    HIGH(Bar::getHigh),
    LOW(Bar::getLow),
    VOLUME(Bar::getVolume);

    public final ToDoubleFunction<Bar> extractor;
    Price(ToDoubleFunction<Bar> extractor) { this.extractor = extractor; }
    public static Price parsePrice(String s) {
        try {
            return Price.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown price: " + s
                + " (expected one of CLOSE, OPEN, HIGH, LOW, VOLUME)", ex);
        }
    }
}
