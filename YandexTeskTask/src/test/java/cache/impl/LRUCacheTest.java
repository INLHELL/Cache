package cache.impl;

import cache.api.Cache;
import cache.api.Cache.TYPE;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Created by user on 26.05.2015.
 */
public class LRUCacheTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testSingleElementCache() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 1);
        cache.put(1, "a");
        cache.put(2, "b");
        Assert.assertNotNull(cache.get(2));
        Assert.assertNull(cache.get(1));
        Assert.assertEquals("b", cache.get(2));
    }

    @Test
    public void testPropagatedElementWillSurvive() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.get(1);
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(4, "d");
        cache.put(5, "e");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(4));
        Assert.assertNotNull(cache.get(5));
        Assert.assertNull(cache.get(2));
        Assert.assertNull(cache.get(3));
        Assert.assertEquals("a", cache.get(1));
    }


    @Test
    public void testPropagationWorkWhenNewValuePut() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.put(1, "aa");
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(4, "d");
        cache.put(5, "e");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(4));
        Assert.assertNotNull(cache.get(5));
        Assert.assertNull(cache.get(2));
        Assert.assertNull(cache.get(3));
        Assert.assertEquals("aa", cache.get(1));
    }

    @Test
    public void testPropagationWorkWhenNewValuesPut() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.put(1, "aa");
        cache.put(2, "bb");
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(4, "d");
        cache.put(1, "aaa");
        cache.put(2, "bbb");
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(5, "e");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(2));
        Assert.assertNotNull(cache.get(5));
        Assert.assertNull(cache.get(3));
        Assert.assertNull(cache.get(4));
        Assert.assertEquals("aaa", cache.get(1));
        Assert.assertEquals("bbb", cache.get(2));
        Assert.assertEquals("e", cache.get(5));
    }


    @Test
    public void testPropagatedElementsWillSurvive() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.get(1);
        cache.get(2);
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(4, "d");
        cache.get(1);
        cache.get(2);
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(5, "e");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(2));
        Assert.assertNotNull(cache.get(5));
        Assert.assertNull(cache.get(3));
        Assert.assertNull(cache.get(4));
        Assert.assertEquals("a", cache.get(1));
        Assert.assertEquals("b", cache.get(2));
        Assert.assertEquals("e", cache.get(5));
    }


    @Test
    public void testPropagationByPutNewValueAndGet() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.get(1);
        cache.get(2);
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(4, "d");
        cache.get(1);
        cache.put(2, "bb");
        cache.put(1, "aa");
        cache.get(4);
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        cache.put(5, "e");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(4));
        Assert.assertNotNull(cache.get(5));
        Assert.assertNull(cache.get(3));
        Assert.assertNull(cache.get(2));
        Assert.assertEquals("aa", cache.get(1));
        Assert.assertEquals("d", cache.get(4));
        Assert.assertEquals("e", cache.get(5));
    }


    @Test
    public void testPutThrowsExceptionForNullKey() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 1);
        this.thrown.expect(NullPointerException.class);
        cache.put(null, "a");
        Assert.assertNull(cache.get(1));
    }

    @Test
    public void testPutThrowsExceptionForNullValue() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 1);
        this.thrown.expect(NullPointerException.class);
        cache.put(1, null);
    }

    @Test
    public void testGetReturnsNullIfNullPassed() {
        Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 1);
        cache.put(1, "a");
        Assert.assertNull(cache.get(null));
        Assert.assertNotNull(cache.get(1));
    }
}
