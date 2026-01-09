package de.mecrytv;

import de.mecrytv.cache.CacheService;
import de.mecrytv.cache.GenericCacheNode;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;
import de.mecrytv.utils.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DatabaseAPI {
    private static DatabaseAPI instance;
    private final RedisManager redisManager;
    private final MariaDBManager mariaDBManager;
    private final CacheService cacheService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DatabaseAPI(DatabaseConfig config, String modelPackage) {
        instance = this;
        this.redisManager = new RedisManager(config);
        this.mariaDBManager = new MariaDBManager(config);
        this.cacheService = new CacheService();

        this.cacheService.initialize(modelPackage);

        startFlushScheduler(5);
    }

    public static DatabaseAPI getInstance() { return instance; }

    public String getGeneric(String database, String table, String keyColumn, String valueColumn, String identifier) {
        String redisKey = "cache:" + database + ":" + table + ":" + identifier;
        String cachedValue = redisManager.get(redisKey);

        if (cachedValue != null) {
            return cachedValue;
        }

        String query = String.format("SELECT %s FROM %s.%s WHERE %s = ? LIMIT 1",
                valueColumn, database, table, keyColumn);

        try (Connection conn = mariaDBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, identifier);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String result = rs.getString(valueColumn);

                redisManager.setex(redisKey, 1800, result);
                return result;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseAPI] Fehler bei Abfrage von " + database + "." + table + ": " + e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> T get(String nodeName, String id) {
        return (T) instance.cacheService.getNode(nodeName).get(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> void set(String nodeName, T model) {
        ((GenericCacheNode<T>) instance.cacheService.getNode(nodeName)).set(model);
    }

    public static void delete(String nodeName, String id) {
        instance.cacheService.getNode(nodeName).delete(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> List<T> getAll(String nodeName) {
        return (List<T>) instance.cacheService.getNode(nodeName).getAll();
    }

    public <T extends ICacheModel> void registerModel(String tableName, Supplier<T> factory) {
        GenericCacheNode<T> node = new GenericCacheNode<>(tableName, factory, redisManager, mariaDBManager);
        cacheService.registerNode(node);
    }

    private void startFlushScheduler(int minutes) {
        scheduler.scheduleAtFixedRate(cacheService::flushAll, minutes, minutes, TimeUnit.MINUTES);
    }

    public void shutdown() {
        scheduler.shutdown();
        cacheService.flushAll();
        redisManager.disconnect();
        mariaDBManager.shutDown();
    }

    public CacheService getCacheService() { return cacheService; }
}