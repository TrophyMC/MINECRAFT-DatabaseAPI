package de.mecrytv.mariadb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.mecrytv.utils.DatabaseConfig;
import java.sql.Connection;
import java.sql.SQLException;

public class MariaDBManager {
    private final HikariDataSource dataSource;

    public MariaDBManager(DatabaseConfig config) {
        HikariConfig hConfig = new HikariConfig();
        hConfig.setJdbcUrl("jdbc:mariadb://" + config.mariaHost() + ":" + config.mariaPort() + "/" + config.mariaDatabase());
        hConfig.setUsername(config.mariaUsername());
        hConfig.setPassword(config.mariaPassword());

        hConfig.setMaximumPoolSize(20);
        hConfig.setMinimumIdle(10);
        hConfig.setConnectionTimeout(3000);
        hConfig.setMaxLifetime(1800000);

        hConfig.addDataSourceProperty("cachePrepStmts", "true");
        hConfig.addDataSourceProperty("prepStmtCacheSize", "5120");
        hConfig.addDataSourceProperty("useServerPrepStmts", "true");

        this.dataSource = new HikariDataSource(hConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null) dataSource.close();
    }
}