package de.mecrytv.model;

import com.google.gson.JsonObject;

public interface ICacheModel {
    String getIdentifier();
    JsonObject serialize();
    void deserialize(JsonObject data);
}