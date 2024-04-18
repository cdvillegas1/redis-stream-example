package com.example.demo.config;

import com.example.demo.api.dto.UserDTO;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class RedisConfig {

    public static final String REDIS_HEFESTO_PREFIX = "core_";
    public static final String REDIS_PREFIX = REDIS_HEFESTO_PREFIX;

    @Value("${server.redis}")
    private String redis;

    @Bean(name = "redissonClient", destroyMethod = "shutdown")
    public RedissonClient getRedissonClient() {
        Config config = new Config();

        config.useSingleServer().setAddress(redis);
        config.setExecutor(getExecutorVirtual());

        return Redisson.create(config);
    }

    @Bean(name = "virtualExecutor")
    public ExecutorService getExecutorVirtual() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "autoReverseIfNotConfirmedCache")
    public RMapCache<String, UserDTO> getAutoReverseIfNotConfirmedCache() {
        return getRedissonClient().getMapCache(REDIS_PREFIX + "lock_cadavi", JsonJacksonCodec.INSTANCE);
    }

    @Bean(name = "streamCancelledTrx")
    public RStream<String, UserDTO> getStreamCancelledTrx() {
        return getRedissonClient().getStream(REDIS_PREFIX + "stream_cancelled_trx", JsonJacksonCodec.INSTANCE);
    }
}