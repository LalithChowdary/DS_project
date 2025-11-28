package org.cloudbus.cloudsim.examples.ds.proposed;

import java.util.*;

/**
 * Simulates a Two-Level Redis Cache Architecture.
 * Level 1: Regional (Shared)
 * Level 2: Zonal (Split into Zone 1 for LB1 and Zone 2 for LB2)
 */
public class RedisMock {
    private static RedisMock instance;

    // Level 1 Cache (Regional)
    private Map<String, Map<String, String>> regionalCache;

    // Level 2 Caches (Zonal) - Map<ZoneID, Cache>
    private Map<String, Map<String, Map<String, String>>> zonalCache;

    // TTL Management: Key -> Expiry Time (Simulation Time)
    private Map<String, Double> expiryMap;

    // Pub/Sub: Channel -> List of Subscribers
    private Map<String, List<RedisSubscriber>> subscribers;

    public interface RedisSubscriber {
        void onMessage(String channel, String message);
    }

    private RedisMock() {
        regionalCache = new HashMap<>();
        zonalCache = new HashMap<>();
        expiryMap = new HashMap<>();
        subscribers = new HashMap<>();
    }

    public static RedisMock getInstance() {
        if (instance == null) {
            instance = new RedisMock();
        }
        return instance;
    }

    public static void reset() {
        instance = new RedisMock();
    }

    // --- Helper Methods ---

    private Map<String, Map<String, String>> getCache(int level, String zoneOrRegion) {
        if (level == 1) {
            return regionalCache; // Shared Regional Cache
        } else if (level == 2) {
            // Level 2: Zonal Cache
            if (!zonalCache.containsKey(zoneOrRegion)) {
                zonalCache.put(zoneOrRegion, new HashMap<>());
            }
            return zonalCache.get(zoneOrRegion);
        }
        return null; // Should not happen with valid level
    }

    // --- Hash Operations (HSET, HGET, HGETALL, DEL) ---

    public void hset(int level, String region, String key, String field, String value) {
        Map<String, Map<String, String>> cache = getCache(level, region);
        if (cache != null) {
            if (!cache.containsKey(key)) {
                cache.put(key, new HashMap<>());
            }
            cache.get(key).put(field, value);
        }
    }

    public String hget(int level, String region, String key, String field) {
        Map<String, Map<String, String>> cache = getCache(level, region);
        if (cache != null && cache.containsKey(key)) {
            return cache.get(key).get(field);
        }
        return null;
    }

    public Map<String, String> hgetAll(int level, String region, String key) {
        Map<String, Map<String, String>> cache = getCache(level, region);
        if (cache != null && cache.containsKey(key)) {
            return new HashMap<>(cache.get(key));
        }
        return null;
    }

    public void del(int level, String region, String key) {
        Map<String, Map<String, String>> cache = getCache(level, region);
        if (cache != null) {
            cache.remove(key);
            expiryMap.remove(key); // Remove TTL if exists
        }
    }

    public Set<String> scanKeys(int level, String region, String prefix) {
        Map<String, Map<String, String>> cache = getCache(level, region);
        Set<String> matches = new HashSet<>();
        if (cache != null) {
            for (String key : cache.keySet()) {
                if (key.startsWith(prefix)) {
                    matches.add(key);
                }
            }
        }
        return matches;
    }

    // --- TTL Operations (SETEX, EXPIRE) ---

    public void setEx(int level, String region, String key, String value, double currentSimTime, double ttlSeconds) {
        // For simplicity, we treat SETEX as setting a field "status" = value in the
        // hash
        hset(level, region, key, "status", value);
        expiryMap.put(key, currentSimTime + ttlSeconds);
    }

    public void expire(String key, double currentSimTime, double ttlSeconds) {
        if (expiryMap.containsKey(key) || existsInAnyCache(key)) {
            expiryMap.put(key, currentSimTime + ttlSeconds);
        }
    }

    private boolean existsInAnyCache(String key) {
        if (regionalCache.containsKey(key))
            return true;
        for (Map<String, Map<String, String>> zoneCache : zonalCache.values()) {
            if (zoneCache.containsKey(key))
                return true;
        }
        return false;
    }

    // --- Simulation Tick (Check for Expired Keys) ---

    /**
     * Checks for expired keys and publishes "expired" events.
     * 
     * @param currentSimTime Current simulation time
     */
    public void tick(double currentSimTime) {
        Iterator<Map.Entry<String, Double>> it = expiryMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Double> entry = it.next();
            if (currentSimTime >= entry.getValue()) {
                String key = entry.getKey();
                it.remove();

                // Publish Expiry Event
                // Format: "expired:<key>"
                publish("keyspace:expired", key);

                // Auto-delete from all caches (Simulating Redis behavior)
                regionalCache.remove(key);
                for (Map<String, Map<String, String>> zoneCache : zonalCache.values()) {
                    zoneCache.remove(key);
                }
            }
        }
    }

    // --- Pub/Sub Operations ---

    public void subscribe(String channel, RedisSubscriber subscriber) {
        if (!subscribers.containsKey(channel)) {
            subscribers.put(channel, new ArrayList<>());
        }
        subscribers.get(channel).add(subscriber);
    }

    public void publish(String channel, String message) {
        if (subscribers.containsKey(channel)) {
            for (RedisSubscriber sub : subscribers.get(channel)) {
                sub.onMessage(channel, message);
            }
        }
    }
}
