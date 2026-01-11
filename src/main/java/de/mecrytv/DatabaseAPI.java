package de.mecrytv;

import de.mecrytv.cache.*;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;
import de.mecrytv.utils.DatabaseConfig;
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

    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    public DatabaseAPI(DatabaseConfig config) {
        instance = this;
        this.redis = new RedisManager(config);
        this.dbManager = new MariaDBManager(config);
        scheduler.scheduleAtFixedRate(cacheService::flushAll, 5, 5, TimeUnit.MINUTES);

        startHeartbeat();
    }

    public static DatabaseAPI getInstance() { return instance; }

    public CompletableFuture<String> getGenericAsync(String database, String table, String keyColumn, String valueColumn, String identifier) {
        String redisKey = "cache:generic:" + database + ":" + table + ":" + identifier;

        return redis.get(redisKey).thenCompose(cached -> {
            if (cached != null) return CompletableFuture.completedFuture(cached);

            return CompletableFuture.supplyAsync(() -> {
                String query = String.format("SELECT %s FROM %s.%s WHERE %s = ? LIMIT 1",
                        valueColumn, database, table, keyColumn);

                try (Connection conn = dbManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(query)) {

                    stmt.setString(1, identifier);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String result = rs.getString(valueColumn);
                        redis.setex(redisKey, 1800, result);
                        return result;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("SQL Fehler in getGenericAsync", e);
                }
                return null;
            }, dbExecutor);
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
}