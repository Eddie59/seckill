package com.shallowan.seckill.redis;

/**
 * @author ShallowAn
 */
public interface KeyPrefix {
    int expireSeconds();

    String getPrefix();
}
