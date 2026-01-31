package de.mecrytv.databaseapi.cache;

import com.google.common.reflect.ClassPath;
import java.lang.reflect.Modifier;
import java.util.*;

public class CacheService {
    private final Map<String, CacheNode<?>> cacheNodes = new HashMap<>();

    public void initialize(String packageName) {
        try {
            ClassPath classPath = ClassPath.from(getClass().getClassLoader());
            for (ClassPath.ClassInfo classInfo : classPath.getTopLevelClassesRecursive(packageName)) {
                Class<?> clazz = classInfo.load();
                if (CacheNode.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                    try {
                        CacheNode<?> node = (CacheNode<?>) clazz.getDeclaredConstructor().newInstance();
                        registerNode(node);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void registerNode(CacheNode<?> node) {
        cacheNodes.put(node.nodeName, node);
        node.createTableIfNotExists();
    }

    public void flushAll() { cacheNodes.values().forEach(CacheNode::flush); }

    @SuppressWarnings("unchecked")
    public <T extends CacheNode<?>> T getNode(String name) { return (T) cacheNodes.get(name); }

    public Collection<CacheNode<?>> getAllNodes() { return cacheNodes.values(); }
}