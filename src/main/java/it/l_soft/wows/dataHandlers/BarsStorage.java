package it.l_soft.wows.dataHandlers;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free, single-writer ring buffer with a fail-fast, zero-copy view.
 */
public class BarsStorage<T> {

    // What we really store in the ring buffer
    private static final class StoredItem<T> {
        final T value;
        final long version;

        StoredItem(T value, long version) {
            this.value = value;
            this.version = version;
        }
    }

    private final StoredItem<T>[] buffer;
    private final int cap;
    private volatile int head = 0;
    private volatile int size = 0;
    private final AtomicLong writes = new AtomicLong(0); // global version

    @SuppressWarnings("unchecked")
    public BarsStorage(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.cap = capacity;
        this.buffer = (StoredItem<T>[]) new StoredItem[capacity]; // ✅ real array
    }

    /** Single writer */
    public void add(T item) {
        long v = writes.incrementAndGet();                  // bump version
        buffer[head] = new StoredItem<>(item, v);           // write slot
        head = (head + 1) % cap;
        if (size < cap) size++;
    }

    /** A stable, zero-copy view; fails fast if buffer is modified during iteration. */
    public View view() {
        long v = writes.get();
        int h = head;
        int s = size;
        int start = (h - s + cap) % cap;
        return new View(start, s, v);
    }

    /** Iterable view over the current contents. */
    public final class View implements Iterable<T> {
        private final int start, len;
        private final long version;

        private View(int start, int len, long version) {
            this.start = start;
            this.len = len;
            this.version = version;
        }

        @Override
        public Iterator<T> iterator() {
            // Optional: verify view is still valid before iteration
            if (writes.get() != version) {
                throw new IllegalStateException("Stale view");
            }

            return new Iterator<T>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    if (writes.get() != version) {
                        throw new IllegalStateException("Stale view");
                    }
                    return i < len;
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    int idx = (start + i++) % cap;
                    StoredItem<T> slot = buffer[idx];
                    if (slot == null) {
                        // Should not happen if size/head are managed correctly
                        throw new IllegalStateException("Uninitialized slot");
                    }
                    return slot.value;
                }
            };
        }
    }
}
