package de.mecrytv.utils;

public record DatabaseConfig(
        String mariaHost, int mariaPort, String mariaDatabase, String mariaUser, String mariaPassword,
        String redisHost, int redisPort, String redisPassword
) {}