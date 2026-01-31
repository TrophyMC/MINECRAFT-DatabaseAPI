package de.mecrytv.databaseapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.mecrytv.databaseapi.cache.*;
import de.mecrytv.databaseapi.mariadb.MariaDBManager;
import de.mecrytv.databaseapi.model.ICacheModel;
import de.mecrytv.databaseapi.redis.RedisManager;
import de.mecrytv.databaseapi.utils.DatabaseConfig;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class DatabaseAPI {

    private static DatabaseAPI instance;
    private final RedisManager redis;
    private final MariaDBManager dbManager;
    private final CacheService cacheService = new CacheService();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Gson gson = new Gson();

    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    public DatabaseAPI(DatabaseConfig config) {
        instance = this;
        this.redis = new RedisManager(config);
        this.dbManager = new MariaDBManager(config);

        scheduler.scheduleAtFixedRate(cacheService::flushAll, 5, 5, TimeUnit.MINUTES);

        startHeartbeat();
    }

    public static DatabaseAPI getInstance() { return instance; }
    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> CompletableFuture<List<T>> getList(String node, String jsonKey, String value) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode == null) return CompletableFuture.completedFuture(List.of());
        return cacheNode.getListAsync(jsonKey, value);
    }
    public CompletableFuture<JsonObject> getGenericAsync(String database, String table, String keyColumn, String valueColumn, String identifier) {
        String redisKey = "cache:generic:" + table + ":" + identifier;

        return redis.get(redisKey).thenCompose(cached -> {
            if (cached != null && !cached.isEmpty()) {
                try {
                    return CompletableFuture.completedFuture(gson.fromJson(cached, JsonObject.class));
                } catch (Exception e) {
                    return CompletableFuture.completedFuture(null);
                }
            }

            return CompletableFuture.supplyAsync(() -> {
                String query = String.format("SELECT %s FROM %s.%s WHERE %s = ? LIMIT 1",
                        valueColumn, database, table, keyColumn);

                try (Connection conn = dbManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(query)) {

                    stmt.setString(1, identifier);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String result = rs.getString(valueColumn);
                        if (result != null) {
                            redis.setex(redisKey, 1800, result);
                            return gson.fromJson(result, JsonObject.class);
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("[DatabaseAPI] SQL Fehler in getGenericAsync: " + e.getMessage());
                }
                return null;
            }, dbExecutor);
        });
    }
    public void setGenericAsync(String database, String table, String keyColumn, String valueColumn, String identifier, JsonObject data) {
        String redisKey = "cache:generic:" + table + ":" + identifier;
        String jsonString = data.toString();

        redis.set(redisKey, jsonString);

        dbExecutor.execute(() -> {
            String query = String.format("INSERT INTO %s.%s (%s, %s) VALUES (?, ?) ON DUPLICATE KEY UPDATE %s = ?",
                    database, table, keyColumn, valueColumn, valueColumn);

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, identifier);
                stmt.setString(2, jsonString);
                stmt.setString(3, jsonString);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[DatabaseAPI] Fehler beim Background-Update von " + table + ": " + e.getMessage());
            }
        });
    }
    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> void set(String node, T model) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode != null) cacheNode.set(model);
    }
    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> CompletableFuture<List<T>> getAll(String node) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode == null) return CompletableFuture.completedFuture(List.of());
        return cacheNode.getAllAsync();
    }
    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> CompletableFuture<T> get(String node, String id) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode == null) return CompletableFuture.completedFuture(null);
        return cacheNode.get(id);
    }
    public <T extends ICacheModel> void registerModel(String name, Supplier<T> factory) {
        cacheService.registerNode(new GenericCacheNode<>(name, factory, redis, dbManager));
    }
    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> void delete(String node, String id) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode != null) {
            cacheNode.delete(id);
        }
    }
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        System.out.println("[DatabaseAPI] Starte finalen Datenbank-Sync...");
        cacheService.flushAll();

        dbExecutor.shutdown();
        redis.disconnect();
        dbManager.shutdown();
        System.out.println("[DatabaseAPI] Alle Verbindungen sauber getrennt.");
    }
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1");
            } catch (SQLException e) {
                System.err.println("[DatabaseAPI] MariaDB Heartbeat fehlgeschlagen: " + e.getMessage());
            }

            redis.ping().exceptionally(ex -> {
                System.err.println("[DatabaseAPI] Redis Heartbeat fehlgeschlagen: " + ex.getMessage());
                return null;
            });

        }, 30, 30, TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> CompletableFuture<Void> updateAsync(String node, String id, JsonObject updates) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Node nicht gefunden"));

        return cacheNode.get(id).thenAccept(model -> {
            if (model == null) throw new RuntimeException("Modell mit ID " + id + " nicht gefunden");
            model.applyUpdate(updates);
            cacheNode.set(model);
        });
    }

    public RedisManager getRedis() {
        return redis;
    }
}