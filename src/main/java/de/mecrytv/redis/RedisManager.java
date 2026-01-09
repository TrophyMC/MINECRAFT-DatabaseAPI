package de.mecrytv.redis;

import de.mecrytv.utils.DatabaseConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.NettyCustomizer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

public class RedisManager {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;

    public RedisManager(DatabaseConfig config) {
        ClientResources res = DefaultClientResources.builder()
                .ioThreadPoolSize(4)
                .computationThreadPoolSize(4)
                .build();

        String auth = config.redisPassword().isEmpty() ? "" : ":" + config.redisPassword() + "@";
        String url = "redis://" + auth + config.redisHost() + ":" + config.redisPort();

        this.client = RedisClient.create(res, url);
        this.connection = client.connect();
        this.async = connection.async();
    }

    public CompletableFuture<String> get(String key) { return async.get(key).toCompletableFuture(); }
    public void set(String key, String val) { async.set(key, val); }
    public void setex(String key, long seconds, String value) { async.setex(key, seconds, value); }
    public void sadd(String key, String member) { async.sadd(key, member); }
    public void srem(String key, String member) { async.srem(key, member); }
    public CompletableFuture<Set<String>> smembers(String key) {
        return async.smembers(key).toCompletableFuture().thenApply(java.util.HashSet::new);
    }

    public void disconnect() {
        connection.close();
        client.shutdown();
    }
}