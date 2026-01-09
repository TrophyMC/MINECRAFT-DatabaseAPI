package de.mecrytv.mariadb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.mecrytv.utils.DatabaseConfig;
import java.sql.Connection;
import java.sql.SQLException;

public class MariaDBManager {
    private final HikariDataSource dataSource;

    public MariaDBManager(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");

        hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.mariaHost() + ":" + config.mariaPort() + "/" + config.mariaDatabase());
        hikariConfig.setUsername(config.mariaUsername());
        hikariConfig.setPassword(config.mariaPassword());

        hikariConfig.setMaximumPoolSize(15);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "5120");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null) dataSource.close();
    }
}