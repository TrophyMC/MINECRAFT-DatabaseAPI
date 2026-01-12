package de.mecrytv.model;

import com.google.gson.JsonObject;

public interface ICacheModel {
    String getIdentifier();
    JsonObject serialize();
    void deserialize(JsonObject data);

    default void applyUpdate(JsonObject updates) {
        JsonObject currentData = serialize();
        updates.asMap().forEach((key, value) -> currentData.add(key, value));
        deserialize(currentData);
    }
}