package de.mecrytv.mariadb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.mecrytv.utils.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

public class MariaDBManager {

    private HikariDataSource dataSource;

    public MariaDBManager(DatabaseConfig config) {
        String host = config.mariaHost();
        int port = config.mariaPort();
        String database = config.mariaDatabase();
        String username = config.mariaUsername();
        String password = config.mariaPassword();

        HikariConfig mariaDBConfig = new HikariConfig();

        mariaDBConfig.setUsername(username);
        mariaDBConfig.setPassword(password);

        mariaDBConfig.setConnectionTimeout(2000);
        mariaDBConfig.setMaximumPoolSize(10);
        mariaDBConfig.setDriverClassName("org.mariadb.jdbc.Driver");

        String jdbcURL = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        mariaDBConfig.setJdbcUrl(jdbcURL);

        dataSource = new HikariDataSource(mariaDBConfig);

        try {
            Connection connection = getConnection();
            closeConnection(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL-Initialisierung fehlgeschlagen", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void closeConnection(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }

    public void shutDown() {
        dataSource.close();
    }
}
