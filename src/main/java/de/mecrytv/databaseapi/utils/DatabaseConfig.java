package de.mecrytv.databaseapi.utils;

public record DatabaseConfig(
        String mariaHost, int mariaPort, String mariaDatabase, String mariaUsername, String mariaPassword,
        String redisHost, int redisPort, String redisPassword
) {}