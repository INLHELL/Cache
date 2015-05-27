package cache.impl;

import cache.api.Cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by Vladislav Fedotov (email:java.lang@yandex.ru) on 23.05.2015 as Yandex test task.
 *
 * This class represents data structure that could be used as cache with specific capacity.<br />
 * Maximal occupied size by this cache is equal to <tt>2*N + N/100<tt/>.<br />
 *
 * If capacity of the cache exceeded at some point of time cache itself removes node based on two strategies:
 * <ul>
 *     <li>LRU - Least recently used node will be removed from the cache</li>
 *     <li>FIFO - Last added node will be removed from the cache</li>
 * </ul>
 */
public class DefaultCache<K, V> implements Cache<K,V> {
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int MINIMUM_QUEUE_CAPACITY = 1 << 7;
    private static final long REASONABLE_OFFER_TIMEOUT_MILLIS = 1L;
    private static final long POLL_GIVE_UP_TIMEOUT_MILLIS = 1_000L;

    private final Map<K, Node<K, V>> map;
    private final int capacity;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock reentrantLock = new ReentrantLock();
    private final TYPE type;

    private volatile boolean shutdown;

    private BlockingQueue<Node<K, V>> recentNodeQueue;
    private Node<K, V> head;
    private Node<K, V> tail;


    /**
     * Constructs cache object based on passed <i>type</i> and <i>capacity</i>.
     *
     * @param type cache type - <b>LRU</b> or <b>FIFO</b>
     * @param capacity cache maximum capacity
     */
    public DefaultCache(TYPE type, int capacity) {
        this.type = type;

        if (capacity < 0) {
            throw new IllegalArgumentException("Illegal cache capacity: " +
                    capacity);
        }

        if (capacity > MAXIMUM_CAPACITY) {
            this.capacity = MAXIMUM_CAPACITY;
        } else {
            this.capacity = capacity;
        }

        this.map = new HashMap<>(this.capacity);

        if (type == TYPE.LRU) {
            final int queueCapacity;
            if (capacity / 100 < MINIMUM_QUEUE_CAPACITY) {
                queueCapacity = MINIMUM_QUEUE_CAPACITY;
            } else {
                queueCapacity = capacity / 100;
            }

            this.recentNodeQueue = new LinkedBlockingQueue<>(queueCapacity);
            final ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(new RecentNodesQueuePoller());
            executorService.shutdown();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final V get(final K key) {
        V value = null;
        if(key != null) {
            this.readWriteLock.readLock().lock();
            try {
                if (this.map.containsKey(key)) {
                    final Node<K, V> node = this.map.get(key);
                    if (this.type == TYPE.LRU) {
                        // If recentNodeQueue if full we will try to add new node during specified timeout
                        this.recentNodeQueue.offer(node, REASONABLE_OFFER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    }
                    value = node.getValue();
                }
            } catch (InterruptedException e) {
                // No actions required
                e.printStackTrace();
            } finally {
                this.readWriteLock.readLock().unlock();
            }
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void put(final K key, final V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key and value must not be null!");
        }
        this.readWriteLock.writeLock().lock();
        try {
            // If map already contains target key
            if (this.map.containsKey(key)) {
                // We will update passed value associated with this key
                final Node<K, V> node = this.map.get(key);
                node.setValue(value);
                // If LRU cache type was chosen, this node should be most recently used in queue
                if (this.type == TYPE.LRU) {
                    this.recentNodeQueue.offer(node);
                }
            }
            // This map does not contain passed key before
            else {
                final Node<K, V> newlyCreatedNode = new Node<>(key, value);
                // If LRU type of cache was chosen then we have to guard dequeue (in case we exceed capacity) by lock
                if (this.type == TYPE.LRU) {
                    this.reentrantLock.lock();
                    try {
                        this.dequeueIfFull();
                        this.enqueue(newlyCreatedNode);
                    } finally {
                        this.reentrantLock.unlock();
                    }
                } else {
                    this.dequeueIfFull();
                    this.enqueue(newlyCreatedNode);
                }
                this.map.put(key, newlyCreatedNode);
            }
        } finally {
            this.readWriteLock.writeLock().unlock();
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.map.size();
    }

    /**
     * By calling this method you inform cache, that you want stop using it.<br />
     * Shutdown process might take some time, so it isn't a intermediate action.<br />
     * <b>This method won't be used in real life.</b>
     */
    public void shutdown() {
        this.shutdown = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString() {
        final StringBuffer sb = new StringBuffer("Cache{");
        sb.append("type=").append(this.type);
        sb.append(", capacity=").append(this.capacity);
        sb.append(", map=").append(this.map);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Dequeues node from the queue if its full (full means size of the <tt>map</tt> is equal to specified <tt>capacity</tt>).<br />
     * This method used in both strategies of the cache LRU and FIFO.<br />
     * It has further logic, removing node from the <tt>map</tt> and setting new <i>tail</i> node for the queue to
     * <i>previous</i> node of the old <i>tail</i>.
     */
    private void dequeueIfFull() {
        if (this.map.size() == this.capacity) {
            this.map.remove(this.tail.getKey());
            this.tail = this.tail.getPrevious();
            if (this.tail != null) {
                this.tail.setNext(null);
            }
        }
    }

    /**
     * Makes passed existing in the queue node most recent, which simply means puts it to the head of the queue
     * (used only if <b>LRU</b> was chosen).<br />
     * First, it removes it from current position and then enqueues it to the <i>head</i> of the queue.
     *
     * @param node node that will be propagated as most recently used.
     */
    private void makeExistingMostRecent(final Node<K, V> node) {
        this.removeFromCurrentPosition(node);
        this.enqueue(node);
    }

    /**
     * Puts passed node to the head of the queue.<br />
     * Previously used <i>head</i> will be refer to the current node,
     * by <i>previous</i> reference, current node will refer to the old <i>head</i> by <i>next</i> reference.<br />
     * Reference to the <i>previous</i> node of the current will be stay <b>null</b>.
     * If <i>head<i/> and <i>tail</i> references of this queue are <i>null</i> (queue is <b>empty</b>), both references
     * of the current node will be <b>null</b>.
     *
     * @param node node object that will put to the head of the queue
     */
    private void enqueue(Node<K, V> node) {
        node.setNext(this.head);
        node.setPrevious(null);

        // If current node it not a single node in cache, 'head' has to be update as well
        if (this.head != null) {
            this.head.setPrevious(node);
        }

        this.head = node;

        // If current node is a single node in cache, 'tail' has to be updated as well
        if (this.tail == null) {
            this.tail = node;
        }
    }

    /**
     * Removes passed node from its position in the queue.
     * Removing means that its <i>previous</i> node has to refer to its <i>next<i/> node, by <i>next<i/> reference and
     * and <i>next</i> node has to refer to the <i>previous<i/> node, by <i>previous</i> reference.
     *
     * @param node node object that should be removed from the queue
     */
    private void removeFromCurrentPosition(final Node<K, V> node) {
        final Node<K, V> next = node.getNext();
        final Node<K, V> previous = node.getPrevious();

        // Check if the current node is not the 'head' and contains reference to the 'previous' node
        if (previous != null) {
            previous.setNext(next);
        }
        // Current node was the 'head', it does not contain reference to the 'previous' node, new 'head' will be the 'next' node
        else {
            this.head = next;
        }
        // Check if the current node is not the 'tail' and contains reference to the 'next' node
        if (next != null) {
            next.setPrevious(previous);
        }
        // Current node is the 'tail', it does not contain reference to the 'next' node, new 'tail' will be the 'previous' node
        else {
            this.tail = previous;
        }
    }

    /**
     * This class represent doubly-linked node, that is used for representing doubly-linked queue,
     * each node has to references, to the next and to the previous nodes.<br />
     * If only single node was created both references will be <i>null<i/>.<br />
     * If current node stays in the <i>head<i/> of the queue, its <i>previous</i> reference will be <i>null</i>.<br />
     * If current node stays in the <i>tail<i/> of the queue, its <i>next</i> reference will be <i>null</i>.<br />
     * In other cases node references must be <i>non-null</i>.
     *
     * @param <K> key type of the current node
     * @param <V> value type of the current node
     */
    private static final class Node<K, V> {
        /**
         * Key of the current node.
         */
        private final K key;

        /**
         * Value of the current node.
         */
        private V value;

        /**
         * Reference to the next node.
         * This object might be <i>null<i/> if current node is <i>tail<i/> of the queue.
         */
        private Node<K, V> next;

        /**
         * Reference to the previous node.
         * This object might be <i>null<i/> if current node is <i>head<i/> of the queue.
         */
        private Node<K, V> previous;

        /**
         * Constructs node.
         *
         * @param key   key of the current node
         * @param value value of the current node
         */
        private Node(K key, V value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Returns key of the current node.
         *
         * @return key of the current node
         */
        private K getKey() {
            return this.key;
        }

        /**
         * Returns value of the current node.
         *
         * @return value of the current node
         */
        private V getValue() {
            return this.value;
        }

        /**
         * Sets value of the current node.
         *
         * @param value value of the current node
         */
        private void setValue(V value) {
            this.value = value;
        }

        /**
         * Returns reference to the next node from the current node.
         *
         * @return reference to the next node, might be <i>null<i/> if current node is <i>tail<i/> of the queue
         */
        private Node<K, V> getNext() {
            return this.next;
        }

        /**
         * Sets reference to the next node from current node.
         *
         * @param next node object, might be <i>null</i> if current node is <i>tail<i/> of the queue
         */
        private void setNext(final Node<K, V> next) {
            this.next = next;
        }

        /**
         * Returns reference to the previous node from the current node.
         *
         * @return reference to the previous node, might be <i>null<i/> if current node is <i>head<i/> of the queue
         */
        private Node<K, V> getPrevious() {
            return this.previous;
        }

        /**
         * Sets reference to the previous node from current node.
         *
         * @param previous node object, might be <i>null</i> if current node is <i>head<i/> of the queue
         */
        private void setPrevious(final Node<K, V> previous) {
            this.previous = previous;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean equals(Object obj) {
            if (!(obj instanceof Node)) {
                return false;
            }
            final Node otherNode = (Node) obj;
            final Object thisKey = this.key;
            final Object otherKey = otherNode.key;
            return thisKey == otherKey || (thisKey != null && thisKey.equals(otherKey));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final int hashCode() {
            return Objects.hashCode(this.key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final String toString() {
            final StringBuilder sb = new StringBuilder("Node{");
            sb.append("key=").append(this.key);
            sb.append(", value=").append(this.value);
            sb.append('}');
            return sb.toString();
        }

    }

    /**
     * Main purpose of this class to poll the queue that contains nodes that has to be propagated as most recent.
     */
    private class RecentNodesQueuePoller implements Callable<Void> {
        /**
         * {@inheritDoc}
         */
        @Override
        public final Void call() {
            while (!Thread.interrupted() && !DefaultCache.this.shutdown) {
                Node<K, V> node = null;
                try {
                    node = DefaultCache.this.recentNodeQueue.poll(POLL_GIVE_UP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (node != null) {
                    DefaultCache.this.reentrantLock.lock();
                    try {
                        DefaultCache.this.makeExistingMostRecent(node);
                    } finally {
                        DefaultCache.this.reentrantLock.unlock();
                    }
                }
            }
            return null;
        }
    }

}
