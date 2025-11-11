package it.l_soft.wows.dataHandlers;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import it.l_soft.wows.comms.MarketBar;

public class BarsStorage<marketBars>  {
	
	private class StoredBar {
		public MarketBar bar;
		public long version;
		
		public StoredBar(MarketBar bar, long version) {
			this.bar = bar;
			this.version = version;
		}
	}

    private final StoredBar[] marketBars;
    private final int cap;
    private volatile int head = 0;
    private volatile int size = 0;
    private final AtomicLong writes = new AtomicLong(0); // version

    public BarsStorage(int capacity) {
        this.cap = capacity;
        this.marketBars = new StoredBar[capacity];
    }

	    // single writer
    public void add(MarketBar marketBar) {
    	marketBars[head] = new StoredBar(marketBar, writes.getAndIncrement());              // plain ref write
        head = (head + 1) % cap;
	    if (size < cap) size++;
    }

    /** A stable, zero-copy view; fails fast if overwritten during iteration. */
    public View view() {
        long v = writes.get();
        int h = head;
        int s = size;
        int start = (h - s + cap) % cap;
        return new View(start, s, v);
    }

    public final class View implements Iterable<T> {
        private final int start, len;
        private final long version;

        private View(int start, int len, long version) {
            this.start = start; this.len = len; this.version = version;
        }

        @Override
        public Iterator<T> iterator() {
            // Optional: verify before iteration
            if (writes.get() != version) throw new IllegalStateException("Stale view");
            return new Iterator<T>() {
                int i = 0;
                @Override public boolean hasNext() {
                    if (writes.get() != version) throw new IllegalStateException("Stale view");
                    return i < len;
                }
                @SuppressWarnings("unchecked")
                @Override public T next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    int idx = (start + i++) % cap;
                    return (T) buf[idx];
                }
            };
        }
    }

}
