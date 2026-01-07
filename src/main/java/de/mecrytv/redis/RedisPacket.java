package de.mecrytv.redis;

import com.google.gson.JsonObject;

public record RedisPacket(String type, JsonObject data) {}