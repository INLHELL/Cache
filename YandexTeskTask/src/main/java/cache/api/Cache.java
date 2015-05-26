package cache.api;

/**
 * Created by Vladislav Fedotov (email:java.lang@yandex.ru) on 23.05.2015 as Yandex test task.
 *
 * This interface represents API methods for the cache.
 */
public interface Cache<K, V> {

    /**
     * Returns the value associated with the passed key from the cache,
     * if key does not exist in the cache <b>null</b> will be returned.<br />
     * <b>Pay attention</b>, element that is <tt>put</tt> to the cache will be available for <tt>get</tt> operation
     * after some timeout that depends current load (number of readers and writes) and the cache <tt>capacity</tt>.
     *
     * @param key key that will be used for value lookup
     * @return value associated withe the passed key or <b>null</b> if key does not exist
     */

    V get(K key);

    /**
     * Puts key-value pair to the cache.<br />
     * Depending of chosen strategy in case cache is full (its size is equal to specified <tt>capacity</tt>)
     * least recently used or last put node will be removed from the cache.<br />
     * <b>Pay attention</b>, element that is <tt>put</tt> to the cache will be available for <tt>get</tt> operation
     * after some timeout that depends current load (number of readers and writes) and the cache <tt>capacity</tt>.
     *
     * @param key   key associated with the value that will be added to the cache, must be <b>not null</b>, otherwise {@link NullPointerException} will be thrown
     * @param value value associated with the key that will be added to the cache, must be <b>not null</b>, otherwise {@link NullPointerException} will be thrown
     */

    void put(K key, V value);

    /**
     * Cache type.
     */
    public enum TYPE {
        /**
         * Least recently used.
         * When cache is full discards the least recently used items first.
         */
        LRU,
        /**
         * First-Ib-First-Out (queue).
         * When cache is full the oldest (first) entry, or 'head' of the queue, is discarded first.
         */
        FIFO
    }
}
