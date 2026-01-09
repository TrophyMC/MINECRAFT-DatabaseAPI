package de.mecrytv.cache;

import com.google.gson.JsonObject;
import de.mecrytv.mariadb.MariaDBManager;
import de.mecrytv.model.ICacheModel;
import de.mecrytv.redis.RedisManager;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

public class GenericCacheNode<T extends ICacheModel> extends CacheNode<T> {

    public GenericCacheNode(String nodeName, Supplier<T> factory, RedisManager r, MariaDBManager d) {
        super(nodeName, factory, r, d);
    }

    @Override
    public List<T> getAllFromDatabase() {
        List<T> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM " + nodeName)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                T model = factory.get();
                model.deserialize(gson.fromJson(rs.getString("data"), JsonObject.class));
                list.add(model);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    @Override
    protected T loadFromDatabase(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM " + nodeName + " WHERE id = ?")) {
            ps.setString(1, id);
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
    protected void saveToDatabase(Connection conn, String id, String json) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + nodeName + " (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = ?")) {
            ps.setString(1, id); ps.setString(2, json); ps.setString(3, json);
            ps.executeUpdate();
        }
    }

    @Override
    public void createTableIfNotExists() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + nodeName + " (id VARCHAR(64) PRIMARY KEY, data LONGTEXT)")) {
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}