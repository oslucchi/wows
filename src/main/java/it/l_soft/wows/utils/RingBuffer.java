package it.l_soft.wows.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * A generic, single-producer / multi-consumer ring buffer with overwrite semantics.
 *
 * - When the producer writes more than capacity items, oldest items are overwritten.
 * - Each consumer gets a ConsumerHandle that tracks its own read position.
 * - Consumers can block until new data is available.
 * - If a consumer falls behind and its next item is overwritten, a MissedItemsException is thrown.
 */
public class RingBuffer<T> {

    private final Object[] buffer;
    private final int capacity;

    // Sequence of the last published item. Starts at -1 (no items yet).
    private long cursor = -1L;

    // Next sequence to assign to the producer.
    private long nextSequenceToPublish = 0L;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    /**
     * Publish a new item into the ring buffer.
     * Overwrites the oldest entry when the ring is full.
     *
     * @return the sequence number assigned to this item.
     */
    public long publish(T value) {
        lock.lock();
        try {
            long seq = nextSequenceToPublish++;
            int index = (int) (seq % capacity);
            buffer[index] = value;
            cursor = seq;               // last published sequence
            notEmpty.signalAll();       // wake up all waiting consumers
            return seq;
        } 
        finally {
            lock.unlock();
        }
    }

    public long getLength()
    {
    	return (nextSequenceToPublish > capacity ? capacity : nextSequenceToPublish);
    }
    
    public long getNumberOfObjectsWritten()
    {
    	return nextSequenceToPublish;
    }
    
    
    /**
     * Create a new consumer starting from "now".
     *
     * The consumer will:
     * - Block until a sequence >= its nextSequence is available.
     * - Detect if it missed items due to overwrite.
     */
    public ConsumerHandle createConsumer() {
        lock.lock();
        try {
            long startSeq = cursor + 1; // consumer will see only future items
            return new ConsumerHandle(this, startSeq);
        } 
        finally {
            lock.unlock();
        }
    }

    // =============== Inner classes ===============

    public static final class ValueWithSequence<T> {
        private final long sequence;
        private final T value;

        public ValueWithSequence(long sequence, T value) {
            this.sequence = sequence;
            this.value = value;
        }

        public long getSequence() {
            return sequence;
        }

        public T getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "ValueWithSequence{" +
                   "sequence=" + sequence +
                   ", value=" + value +
                   '}';
        }
    }

    /**
     * Exception thrown when a consumer has fallen behind and some items
     * that it should have read were overwritten by the producer.
     */
    public static class MissedItemsException extends Exception {
        private static final long serialVersionUID = 1L;
		private final long expectedSequence;
        private final long lastAvailableSequence;
        private final long missedCount;

        public MissedItemsException(long expectedSequence,
                                    long lastAvailableSequence,
                                    long missedCount) {
            super("Consumer missed " + missedCount + " items. " +
                  "Expected sequence " + expectedSequence +
                  ", but buffer has already moved to " + lastAvailableSequence);
            this.expectedSequence = expectedSequence;
            this.lastAvailableSequence = lastAvailableSequence;
            this.missedCount = missedCount;
        }

        public long getExpectedSequence() {
            return expectedSequence;
        }

        public long getLastAvailableSequence() {
            return lastAvailableSequence;
        }

        public long getMissedCount() {
            return missedCount;
        }
    }

    /**
     * Handle for a single consumer.
     * Each consumer tracks its own nextSequence and reads sequentially.
     */
    public final class ConsumerHandle {

        private final RingBuffer<T> parent;
        private long nextSequence; // next sequence this consumer expects to read

        private ConsumerHandle(RingBuffer<T> parent, long startSequence) {
            this.parent = parent;
            this.nextSequence = (nextSequence > capacity ? nextSequence + 1 : 0);
        }

        /**
         * Blocking read:
         * - Wait until the next item for this consumer is available.
         * - Return it together with its sequence.
         * - If the consumer has fallen behind and items were overwritten,
         *   throw MissedItemsException.
         */
        public ValueWithSequence<T> take() throws InterruptedException, MissedItemsException {
            parent.lock.lock();
            try {
                for (;;) {
                    long availableSeq = parent.cursor;

                    // No new items yet for this consumer: wait
                    if (availableSeq < nextSequence) {
                        parent.notEmpty.await();
                        continue; // re-check after wakeup
                    }

                    // Check for overwrite: if distance >= capacity, we lost data
                    long distance = availableSeq - nextSequence;
                    if (distance >= parent.capacity) {
                        long missed = distance - parent.capacity + 1;
                        throw new MissedItemsException(nextSequence, availableSeq, missed);
                    }

                    // We have data and haven't missed anything
                    int index = (int) (nextSequence % parent.capacity);
                    @SuppressWarnings("unchecked")
                    T value = (T) parent.buffer[index];
                    long seq = nextSequence;
                    nextSequence++;
                    return new ValueWithSequence<>(seq, value);
                }
            } finally {
                parent.lock.unlock();
            }
        }

        /**
         * Non-blocking variant.
         *
         * @return next value if available, or null if no new data.
         * @throws MissedItemsException if items were overwritten before this consumer read them.
         */
        public ValueWithSequence<T> poll() throws MissedItemsException {
            parent.lock.lock();
            try {
                long availableSeq = parent.cursor;

                if (availableSeq < nextSequence) {
                    // nothing new
                    return null;
                }

                long distance = availableSeq - nextSequence;
                if (distance >= parent.capacity) {
                    long missed = distance - parent.capacity + 1;
                    throw new MissedItemsException(nextSequence, availableSeq, missed);
                }

                int index = (int) (nextSequence % parent.capacity);
                @SuppressWarnings("unchecked")
                T value = (T) parent.buffer[index];
                return new ValueWithSequence<>(nextSequence++, value);

            } finally {
                parent.lock.unlock();
            }
        }
        
        /**
         * Get the element corresponding to the specified sequence.
         *
         * @return next value if available, or null if no new data.
         * @throws MissedItemsException if items were overwritten before this consumer read them.
         */
        public ValueWithSequence<T> get(long sequence) throws MissedItemsException {
            parent.lock.lock();
            try {
                int index = (int) (sequence % parent.capacity);
                @SuppressWarnings("unchecked")
                T value = (T) parent.buffer[index];
                return new ValueWithSequence<>(sequence, value);

            } finally {
                parent.lock.unlock();
            }
        }

        /**
         * @return the sequence number this consumer will read next.
         */
        public long getNextSequence() {
            return nextSequence;
        }

		@SuppressWarnings("unchecked")
		public List<T> getContentAsList() {
	    	ArrayList<T> list = new ArrayList<T>();
	    	if (nextSequenceToPublish == 0) return list;
	    		    	
            parent.lock.lock();
            try {
    	    	long n = nextSequenceToPublish; // total items ever written
    	    	long startSeq = Math.max(0, n - capacity); // first sequence still in buffer

    	    	for (long seq = startSeq; seq < n; seq++) {
    	    	    int index = (int) (seq % capacity);
    	    	    list.add((T) parent.buffer[index]);
    	    	}
            } finally {
                parent.lock.unlock();
            }
	    	return list;
		}
    }
}

