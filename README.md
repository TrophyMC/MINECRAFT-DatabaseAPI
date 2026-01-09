# 📦 DatabaseAPI
Eine leistungsstarke, hybride Datenbank-API für Java-Anwendungen (optimiert für Minecraft-Netzwerke). Sie kombiniert **Redis** für extrem schnelle Ladezeiten (Caching) mit **MariaDB** für dauerhafte Datensicherheit.

## ✨ Features
- 🚀 Hybrid-System: Automatisches Caching über Redis (Cache-Aside Pattern).
🛠️ Generic CRUD: Erstellen, Lesen, Aktualisieren und Löschen von Daten ohne eine einzige Zeile SQL.
- 🔄 Auto-Sync: Ein Hintergrund-Task (Scheduler) schreibt geänderte Daten automatisch von Redis in die MariaDB.
- 🧩 Zero Boilerplate: Keine manuellen DAOs oder Repositories nötig – ein Model reicht.
- 🌍 Global Access: "Profi-Weg" Zugriff über statische Methoden von überall im Projekt.

## 🚀 Installation

### 1. In Maven Local publizieren
Führe im Hauptverzeichnis der API folgenden Befehl aus:
```bash
./gradlew publishToMavenLocal
```

### 2. In deinem Projekt einbinden (Gradle & Maven)

#### Gradle
Füge in deiner `build.gradle` Datei folgendes hinzu:
```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'de.mecrytv:DatabaseAPI:1.1.2'
}
```

#### Maven
Füge in deiner `pom.xml` Datei folgendes hinzu:
```xml
<repositories>
    <repository>
        <id>local-repo</id>
        <url>file://${user.home}/.m2/repository</url>
    </repository>
    <repository>
        <id>central</id>
        <url>https://repo.maven.apache.org/maven2</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>de.mecrytv</groupId>
        <artifactId>DatabaseAPI</artifactId>
        <version>1.0.0</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

## 💡 Quick Start

### 1. Model erstellen (Beispiel: UserProfile)
Implementiere das `ICacheModel` Interface. Dank JSON-Serialisierung werden neue Felder automatisch gespeichert.
```java
public class UserProfile implements ICacheModel {
    private String uuid;
    private int coins;

    public UserProfile() {} // Wichtig für die API!

    @Override public String getIdentifier() { return uuid; }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid);
        json.addProperty("coins", coins);
        return json;
    }

    @Override
    public void deserialize(JsonObject data) {
        this.uuid = data.get("uuid").getAsString();
        this.coins = data.get("coins").getAsInt();
    }
    // Getter & Setter...
}
```

### 2. API initialisieren
Starte die API beim Laden deines Plugins/Programms:
```java
DatabaseConfig config = new DatabaseConfig(
    "localhost", 3306, "my_db", "user", "pass", // MariaDB
    "localhost", 6379, "redis_pass"              // Redis
);

DatabaseAPI api = new DatabaseAPI(config, "de.yourproject.models");
api.registerModel("users", UserProfile::new);
```

### 3. Daten verwenden
Greife von **jedem Package** aus direkt auf deine Daten zu:
```java
// GET: Lädt aus Redis (oder DB, falls nicht in Redis)
UserProfile profile = DatabaseAPI.get("users", "UUID-123");

// SET: Speichert in Redis und markiert für DB-Update
profile.setCoins(500);
DatabaseAPI.set("users", profile);

// DELETE: Entfernt aus Cache & Datenbank
DatabaseAPI.delete("users", "UUID-123");

// GET ALL: Lädt alle Einträge aus der MariaDB
List<UserProfile> allUsers = DatabaseAPI.getAll("users");
```

## ⚙️ Funktionsweise: Cache-Aside Pattern
1. **Laden:** Die API prüft zuerst Redis. Ist der Key dort nicht vorhanden, wird die MariaDB abgefragt und Redis automatisch aktualisiert.
2. **Speichern:** Daten werden sofort in Redis geschrieben und im "Dirty-Set" markiert.
3. **Flush:** Ein automatischer Scheduler (Standard: alle 5 Minuten) schreibt alle geänderten Daten gesammelt in die MariaDB.
