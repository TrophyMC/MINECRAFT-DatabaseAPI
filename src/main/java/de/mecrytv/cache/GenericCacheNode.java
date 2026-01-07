package de.mecrytv.cache;

import com.google.gson.JsonObject;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GenericCacheNode<T extends ICacheModel> extends CacheNode<T> {

    public GenericCacheNode(String nodeName, Supplier<T> factory, RedisManager redis, MariaDBManager db) {
        super(nodeName, factory, redis, db);
    }

    @Override
    public void createTableIfNotExists() {
        try (Connection conn = mariaDBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + nodeName + " (id VARCHAR(64) PRIMARY KEY, data LONGTEXT)")) {
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public List<T> getAllFromDatabase() {
        List<T> models = new ArrayList<>();
        try (Connection conn = mariaDBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM " + nodeName)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                T model = factory.get();
                model.deserialize(gson.fromJson(rs.getString("data"), JsonObject.class));
                models.add(model);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return models;
    }

    @Override
    protected void saveToDatabase(Connection conn, T model) throws SQLException {
        String jsonString = model.serialize().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + nodeName + " (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = ?")) {
            ps.setString(1, model.getIdentifier());
            ps.setString(2, jsonString);
            ps.setString(3, jsonString);
            ps.executeUpdate();
        }
    }

    @Override
    protected T loadFromDatabase(String identifier) {
        try (Connection conn = mariaDBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM " + nodeName + " WHERE id = ?")) {
            ps.setString(1, identifier);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                T model = factory.get();
                model.deserialize(gson.fromJson(rs.getString("data"), JsonObject.class));
                return model;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    @Override
    protected void removeFromDatabase(Connection conn, String identifier) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + nodeName + " WHERE id = ?")) {
            ps.setString(1, identifier);
            ps.executeUpdate();
        }
    }
}