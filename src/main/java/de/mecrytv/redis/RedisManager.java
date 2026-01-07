package de.mecrytv.redis;

import com.google.gson.Gson;
import de.mecrytv.utils.DatabaseConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RedisManager {

    private final Gson gson = new Gson();
    private final Map<String, IRedisMessageListener> handlers = new HashMap<>();
    private RedisClient client;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private StatefulRedisConnection<String, String> connection;

    public RedisManager(DatabaseConfig config){
        String host = config.redisHost();
        int port = config.redisPort();
        String password = config.redisPassword();;

        String url = String.format("redis://%s@%s:%d", password, host, port);
        this.client = RedisClient.create(url);
        this.connection = client.connect();
        this.pubSubConnection = client.connectPubSub();

        pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                RedisPacket packet = gson.fromJson(message, RedisPacket.class);
                if (handlers.containsKey(packet.type())) {
                    handlers.get(packet.type()).onReceive(packet.data());
                }
            }
        });

        pubSubConnection.sync().subscribe("trophymc:database:api");
    }

    public void set(String key, String value) {
        connection.sync().set(key, value);
    }

    public String get(String key) {
        return connection.sync().get(key);
    }

    public void sadd(String key, String member) {
        connection.sync().sadd(key, member);
    }

    public Set<String> smembers(String key) {
        return connection.sync().smembers(key);
    }

    public void srem(String key, String member) {
        connection.sync().srem(key, member);
    }

    public long incr(String key) {
        return connection.sync().incr(key);
    }

    public boolean expire(String key, long seconds) {
        return connection.sync().expire(key, seconds);
    }

    public boolean exists(String key) {
        return connection.sync().exists(key) > 0;
    }

    public void setex(String key, long seconds, String value) {
        connection.sync().setex(key, seconds, value);
    }

    public void publish(String type, com.google.gson.JsonObject data) {
        RedisPacket packet = new RedisPacket(type, data);
        connection.sync().publish("trophymc:database:api", gson.toJson(packet));
    }

    public void del(String key) {
        connection.sync().del(key);
    }

    public Set<String> keys(String pattern) {
        return connection.sync().keys(pattern).stream().collect(java.util.stream.Collectors.toSet());
    }

    public boolean sismember(String key, String member) {
        return connection.sync().sismember(key, member);
    }

    public void disconnect() {
        if (connection != null) connection.close();
        if (pubSubConnection != null) pubSubConnection.close();
        if (client != null) client.shutdown();
    }
}
