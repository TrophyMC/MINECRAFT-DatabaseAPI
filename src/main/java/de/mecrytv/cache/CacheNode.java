package de.mecrytv.cache;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class CacheNode<T extends ICacheModel> {
    protected final String nodeName, redisPrefix, dirtySet;
    protected final Supplier<T> factory;
    protected final RedisManager redis;
    protected final MariaDBManager db;
    protected final Gson gson = new Gson();

    public CacheNode(String nodeName, Supplier<T> factory, RedisManager redis, MariaDBManager db) {
        this.nodeName = nodeName;
        this.factory = factory;
        this.redis = redis;
        this.db = db;
        this.redisPrefix = "cache:" + nodeName + ":";
        this.dirtySet = "dirty:" + nodeName;
    }

    public void set(T model) {
        String json = model.serialize().toString();
        redis.set(redisPrefix + model.getIdentifier(), json);
        redis.sadd(dirtySet, model.getIdentifier());
    }

    public CompletableFuture<T> get(String id) {
        return redis.get(redisPrefix + id).thenCompose(json -> {
            if (json != null) {
                T model = factory.get();
                model.deserialize(gson.fromJson(json, JsonObject.class));
                return CompletableFuture.completedFuture(model);
            }

            return CompletableFuture.supplyAsync(() -> loadFromDatabase(id)).thenApply(dbModel -> {
                if (dbModel != null) {
                    redis.setex(redisPrefix + id, 1800, dbModel.serialize().toString());
                }
                return dbModel;
            });
        });
    }
    public CompletableFuture<List<T>> getAllAsync() {
        return CompletableFuture.supplyAsync(this::getAllFromDatabase).thenCompose(dbList ->
                redis.smembers(dirtySet).thenCompose(dirtyIds -> {
                    if (dirtyIds.isEmpty()) return CompletableFuture.completedFuture(dbList);

                    Map<String, T> mergedMap = new HashMap<>();
                    dbList.forEach(m -> mergedMap.put(m.getIdentifier(), m));

                    List<CompletableFuture<Void>> futures = dirtyIds.stream()
                            .map(id -> redis.get(redisPrefix + id).thenAccept(json -> {
                                if (json != null) {
                                    T model = factory.get();
                                    model.deserialize(gson.fromJson(json, JsonObject.class));
                                    mergedMap.put(id, model);
                                }
                            })).toList();

                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> new ArrayList<>(mergedMap.values()));
                })
        );
    }

    public abstract List<T> getAllFromDatabase();
    protected abstract T loadFromDatabase(String id);
    protected abstract void saveToDatabase(Connection conn, String id, String json) throws SQLException;
    public abstract void createTableIfNotExists();

    public void flush() {
        redis.smembers(dirtySet).thenAccept(ids -> {
            if (ids.isEmpty()) return;
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                for (String id : ids) {
                    String json = redis.get(redisPrefix + id).join();
                    if (json != null) {
                        saveToDatabase(conn, id, json);
                        redis.srem(dirtySet, id);
                    }
                }
                conn.commit();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }
}