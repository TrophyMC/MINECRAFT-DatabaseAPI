package de.mecrytv;

import de.mecrytv.cache.*;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;
import de.mecrytv.utils.DatabaseConfig;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class DatabaseAPI {
    private static DatabaseAPI instance;
    private final RedisManager redis;
    private final MariaDBManager dbManager;
    private final CacheService cacheService = new CacheService();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DatabaseAPI(DatabaseConfig config) {
        instance = this;
        this.redis = new RedisManager(config);
        this.dbManager = new MariaDBManager(config);
        scheduler.scheduleAtFixedRate(cacheService::flushAll, 5, 5, TimeUnit.MINUTES);
    }

    public static DatabaseAPI getInstance() { return instance; }

    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> void set(String node, T model) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        if (cacheNode != null) {
            cacheNode.set(model);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends ICacheModel> CompletableFuture<List<T>> getAll(String node) {
        CacheNode<T> cacheNode = (CacheNode<T>) instance.cacheService.getNode(node);
        return (cacheNode != null) ? cacheNode.getAllAsync() : CompletableFuture.completedFuture(List.of());
    }

    public <T extends ICacheModel> void registerModel(String name, Supplier<T> factory) {
        cacheService.registerNode(new GenericCacheNode<>(name, factory, redis, dbManager));
    }

    public void shutdown() {
        scheduler.shutdown();
        cacheService.flushAll();
        redis.disconnect();
        dbManager.shutdown();
    }
}