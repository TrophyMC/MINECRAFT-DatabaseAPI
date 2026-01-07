package de.mecrytv.cache;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public abstract class CacheNode<T extends ICacheModel> {
    protected final String nodeName;
    protected final Supplier<T> factory;
    protected final Gson gson = new Gson();
    protected final String redisPrefix;
    protected final String dirtySetKey;
    protected final String deletedSetKey;
    protected final RedisManager redisManager;
    protected final MariaDBManager mariaDBManager;

    public CacheNode(String nodeName, Supplier<T> factory, RedisManager redisManager, MariaDBManager mariaDBManager) {
        this.nodeName = nodeName;
        this.factory = factory;
        this.redisPrefix = "cache:" + nodeName + ":";
        this.dirtySetKey = "dirty:" + nodeName;
        this.deletedSetKey = "deleted:" + nodeName;
        this.redisManager = redisManager;
        this.mariaDBManager = mariaDBManager;
    }

    public void set(T model) {
        String key = redisPrefix + model.getIdentifier();
        redisManager.set(key, model.serialize().toString());

        redisManager.srem(deletedSetKey, model.getIdentifier());
        redisManager.sadd(dirtySetKey, model.getIdentifier());
    }

    public T get(String identifier) {
        if (redisManager.sismember(deletedSetKey, identifier)) {
            return null;
        }

        String json = redisManager.get(redisPrefix + identifier);
        if (json != null) {
            T model = factory.get();
            model.deserialize(gson.fromJson(json, JsonObject.class));
            return model;
        }

        T dbModel = loadFromDatabase(identifier);
        if (dbModel != null) {
            set(dbModel);
            return dbModel;
        }
        return null;
    }

    public void flush() {
        java.util.Set<String> toSave = redisManager.smembers(dirtySetKey);
        if (!toSave.isEmpty()) {
            try (Connection conn = mariaDBManager.getConnection()) {
                for (String id : toSave) {
                    T model = get(id);
                    if (model != null) {
                        saveToDatabase(conn, model);
                        redisManager.srem(dirtySetKey, id);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        java.util.Set<String> toDelete = redisManager.smembers(deletedSetKey);
        if (!toDelete.isEmpty()) {
            System.out.println("🗑️ Lösche " + toDelete.size() + " Einträge (" + nodeName + ")...");
            try (Connection conn = mariaDBManager.getConnection()) {
                for (String id : toDelete) {
                    removeFromDatabase(conn, id);
                    redisManager.srem(deletedSetKey, id);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void delete(String identifier) {
        String key = redisPrefix + identifier;

        redisManager.del(key);
        redisManager.srem(dirtySetKey, identifier);
        redisManager.sadd(deletedSetKey, identifier);
    }

    public java.util.List<T> getAll() {
        java.util.Map<String, T> mergedData = new java.util.HashMap<>();

        for (T dbModel : getAllFromDatabase()) {
            if (!redisManager.sismember(deletedSetKey, dbModel.getIdentifier())) {
                mergedData.put(dbModel.getIdentifier(), dbModel);
            }
        }

        java.util.Set<String> redisKeys = redisManager.keys(redisPrefix + "*");
        for (String key : redisKeys) {
            String identifier = key.replace(redisPrefix, "");
            T redisModel = get(identifier);

            if (redisModel != null) {
                mergedData.put(identifier, redisModel);
            }
        }

        return new java.util.ArrayList<>(mergedData.values());
    }

    public abstract void createTableIfNotExists();

    public abstract java.util.List<T> getAllFromDatabase();

    protected abstract void removeFromDatabase(Connection conn, String identifier) throws SQLException;

    protected abstract void saveToDatabase(Connection conn, T model) throws SQLException;

    protected abstract T loadFromDatabase(String identifier);
}