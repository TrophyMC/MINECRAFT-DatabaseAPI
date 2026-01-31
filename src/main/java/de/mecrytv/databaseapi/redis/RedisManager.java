package de.mecrytv.databaseapi.redis;

import de.mecrytv.databaseapi.utils.DatabaseConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.function.Consumer;

public class RedisManager {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

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
        this.pubSubConnection = client.connectPubSub();
    }

    public CompletableFuture<String> get(String key) { return async.get(key).toCompletableFuture(); }
    public void set(String key, String val) { async.set(key, val); }
    public void setex(String key, long seconds, String value) { async.setex(key, seconds, value); }
    public void sadd(String key, String member) { async.sadd(key, member); }
    public void srem(String key, String member) { async.srem(key, member); }
    public CompletableFuture<Set<String>> smembers(String key) {
        return async.smembers(key).toCompletableFuture().thenApply(java.util.HashSet::new);
    }
    public void del(String key) {
        async.del(key);
    }
    public CompletableFuture<String> ping() {
        return async.ping().toCompletableFuture();
    }
    public void publish(String channel, String message) {
        async.publish(channel, message);
    }
    public void subscribe(String channel, Consumer<String> messageConsumer) {
        pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String chan, String msg) {
                if (chan.equals(channel)) {
                    messageConsumer.accept(msg);
                }
            }
        });
        pubSubConnection.async().subscribe(channel);
    }

    public void disconnect() {
        connection.close();
        client.shutdown();
    }
}