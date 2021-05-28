package com.info7255.dao;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;


import redis.clients.jedis.Jedis;

@Repository
public class PlanDao {
	public boolean isKeyPresent(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.exists(key);
        }
    }

    public void setHashValue(String key, String field, String value ) {
        try (Jedis jedis = new Jedis("localhost")) {
            jedis.hset(key, field, value);
        }
    }

    public void setKeyValue(String key, String value) {
        try (Jedis jedis = new Jedis("localhost")) {
            jedis.sadd(key, value);
        }
    }

    public Set<String> getKeys(String pattern){
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.keys(pattern);
        }
    }

    public long deleteKeys(String[] keys) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.del(keys);
        }
    }

    public Map<String,String> getAllValuesByKey(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.hgetAll(key);
        }
    }

    public Set<String> getAllMembers(String key) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.smembers(key);
        }
    }

    public String getHashValue(String key, String field) {
        try (Jedis jedis = new Jedis("localhost")) {
            return jedis.hget(key, field);
        }
    }
    
    
}
