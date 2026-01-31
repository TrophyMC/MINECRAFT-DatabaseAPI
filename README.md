# 📦 DatabaseAPI
Eine leistungsstarke, hybride Datenbank-API für Java-Anwendungen (optimiert für Minecraft-Netzwerke). Sie kombiniert **Redis** für extrem schnelle Ladezeiten (Caching) mit **MariaDB** für dauerhafte Datensicherheit.

## ✨ Features
- 🚀 **Hybrid-System:** Automatisches Caching über Redis (Cache-Aside Pattern).
- ⚡ **Async Support:** Volle Unterstützung von `CompletableFuture` für lag-freie Datenbankzugriffe.
- 🛠️ **Generic CRUD:** Erstellen, Lesen, Aktualisieren und Löschen von Daten ohne SQL-Kenntnisse.
- 🔄 **Auto-Sync:** Ein Hintergrund-Task schreibt geänderte Daten automatisch von Redis in die MariaDB.
- 🧩 **Zero Boilerplate:** Keine manuellen DAOs nötig – ein POJO-Model reicht aus.
- 🌍 **Global Access:** Zugriff über ein Singleton-Pattern (`getInstance()`) von überall im Projekt.

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
    implementation 'de.mecrytv:DatabaseAPI:1.2.3'
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

DatabaseAPI api = new DatabaseAPI(dbConfig);
api.registerModel("users", UserProfile::new);
```

### 3. Daten verwenden
Greife von **jedem Package** aus direkt auf deine Daten zu:
```java
// Einzelnen Report laden
DatabaseAPI.<ReportModel>get("reports", "ID123").thenAccept(report -> {
    if (report != null) {
        System.out.println("Grund: " + report.getReason());
    }
});

// Alle Reports laden
DatabaseAPI.<ReportModel>getAll("reports").thenAccept(allReports -> {
    System.out.println("Einträge in DB: " + allReports.size());
});

// Speichern (schreibt sofort in Redis, verzögert in MariaDB)
DatabaseAPI.set("reports", myModel);

// Löschen (entfernt aus Redis & MariaDB)
DatabaseAPI.delete("reports", "ID123");
```

## ⚙️ Funktionsweise: Cache-Aside Pattern
1. **Laden:** Prüft Redis -> Falls leer -> MariaDB -> Cache Update.
2. **Speichern:** Daten gehen sofort in Redis und werden als "dirty" markiert.
3. **Flush:** Ein automatischer Scheduler schreibt alle geänderten Daten gesammelt in festen Intervallen in die MariaDB.
