package codes.antti.auth.authentication;

import codes.antti.auth.common.database.SqliteDatabase;
import codes.antti.auth.common.database.HikariDatabase;
import codes.antti.auth.common.database.Database;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.UUID;

public class AuthenticationCore {
    private AuthenticationWebServer server;
    private AuthenticationDatabase db;
    private int userMaxSessions;

    public void init(AuthPlatformAdapter adapter) {
        this.userMaxSessions = adapter.getUserMaxSessions();

        Path webRoot = adapter.getDataPath().resolve("web");
        try {
            Files.createDirectories(webRoot);
        } catch (IOException ignored) {
        }

        Path loginHtml = webRoot.resolve("login.html");
        if (!Files.exists(loginHtml)) {
            try (InputStream in = AuthenticationCore.class.getResourceAsStream("/assets/authentication/web/login.html")) {
                if (in != null) Files.copy(in, loginHtml);
            } catch (IOException e) {
                adapter.getLogger().severe("Failed to copy default login.html");
                e.printStackTrace();
            }
        }

        String dataPath = adapter.getDataPath().toAbsolutePath().toString();
        String rawUrl = adapter.getDatabaseUrl().replace("{data}", dataPath);
        boolean isSqlite = rawUrl.startsWith("jdbc:sqlite:");

        String driverJar = adapter.getDriverJar();
        String driverClass = adapter.getDriverClass();
        Class<?> externalDriver = null;
        try {
            if (driverJar != null && driverClass != null) {
                URL jarUrl = new File(driverJar.replace("{data}", dataPath)).toURI().toURL();
                ClassLoader loader = new URLClassLoader(new URL[] {jarUrl}, getClass().getClassLoader());
                externalDriver = Class.forName(driverClass, true, loader);
                adapter.getLogger().info("Loaded JDBC driver: " + driverClass + " from " + driverJar);
            } else if (driverClass != null) {
                externalDriver = Class.forName(driverClass);
                adapter.getLogger().info("Loaded JDBC driver: " + driverClass);
            }
        } catch (Exception e) {
            if (driverJar != null) adapter.getLogger().severe("Failed to load JDBC driver " + driverClass + " from " + driverJar);
            else adapter.getLogger().severe("Failed to load JDBC driver " + driverClass);
            e.printStackTrace();
            return;
        }

        Database rawDb;
        try {
            if (isSqlite) {
                if (externalDriver != null) rawDb = new SqliteDatabase(rawUrl.substring("jdbc:sqlite:".length()), (Driver) externalDriver.getDeclaredConstructor().newInstance());
                else rawDb = new SqliteDatabase(rawUrl.substring("jdbc:sqlite:".length()));
            } else {
                if (externalDriver != null) rawDb = new HikariDatabase(rawUrl, (Driver) externalDriver.getDeclaredConstructor().newInstance());
                else rawDb = new HikariDatabase(rawUrl);
            }
        } catch (Exception e) {
            adapter.getLogger().severe("Failed to open database connection");
            e.printStackTrace();
            return;
        }

        try {
            this.db = new AuthenticationDatabase(rawDb, adapter.getAuthTokenLength(), (long) adapter.getSessionLengthDays() * 86400);
        } catch (SQLException e) {
            adapter.getLogger().severe("Failed to initialize database");
            e.printStackTrace();
            rawDb.close();
            return;
        }

        if (adapter.isWebServerEnabled()) {
            try {
                this.server = new AuthenticationWebServer(adapter, this.db);
            } catch (IOException e) {
                adapter.getLogger().severe("Failed to create HTTP server");
                e.printStackTrace();
                return;
            }
        }

        adapter.getLogger().info("Ready to authenticate");
    }

    public void shutdown() {
        if (this.server != null) this.server.close();
        if (this.db != null) this.db.close();
    }

    /**
     * Handles the /auth command. Returns feedback message on success, null on verification failure.
     * Throws SQLException on database error.
     */
    public String handleAuthCommand(UUID playerUuid, String playerName, String token) throws SQLException {
        if (this.db == null) return null;
        if (this.db.verifySession(token, playerUuid.toString(), playerName)) {
            int sessionsCount = this.db.getSessionsCount(playerUuid.toString());
            if (sessionsCount > this.userMaxSessions)
                this.db.deleteOldestSessions(playerUuid.toString(), sessionsCount - this.userMaxSessions);
            return "Verification successful";
        }
        return null;
    }
}
