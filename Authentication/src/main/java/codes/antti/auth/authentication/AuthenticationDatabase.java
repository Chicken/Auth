package codes.antti.auth.authentication;

import codes.antti.auth.common.sqlite.Database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;

public class AuthenticationDatabase extends Database {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();
    private final HashMap<String, Session> sessionCache = new HashMap<>();
    private final HashMap<String, Long> sessionCacheExpiry = new HashMap<>();
    private final AuthenticationPlugin plugin;
    private static final long CACHE_SECONDS = 60;
    private int authTokenLength;
    public long sessionLength;

    public AuthenticationDatabase(@NotNull AuthenticationPlugin plugin) throws SQLException {
        super(plugin.getDataFolder().getAbsolutePath() + "/db.sqlite");
        this.plugin = plugin;
        this.authTokenLength = this.plugin.getConfig().getInt("auth_token_length", 7);
        this.sessionLength = this.plugin.getConfig().getLong("session_length_days", 31) * 24 * 60 * 60;

        this.update(
                "CREATE TABLE IF NOT EXISTS meta (" +
                        "key text NOT NULL," +
                        "value text DEFAULT NULL," +
                        "PRIMARY KEY (key)" +
                        ")"
        );

        ResultSet res = this.query("SELECT value FROM meta WHERE key = ?", "schema");
        int schemaVersion;
        if (res.next()) {
            try {
                schemaVersion = Integer.parseInt(res.getString("value"));
            } catch (NumberFormatException e) {
                throw new SQLException("Invalid schema version");
            }
        } else {
            this.update(
                    "INSERT INTO meta VALUES (?, ?)",
                    "schema", "0"
            );
            schemaVersion = 0;
        }

        int codeSchemaVersion = 3;
        if (schemaVersion < 0 || schemaVersion > codeSchemaVersion) throw new SQLException("Invalid schema version");

        if (schemaVersion < 1) {
            this.update(
                    "CREATE TABLE IF NOT EXISTS sessions (" +
                            "session_id text PRIMARY KEY NOT NULL," +
                            "auth_token text UNIQUE NOT NULL," +
                            "expires integer NOT NULL," +
                            "player_uuid text DEFAULT NULL" +
                            ")"
            );
        }

        if (schemaVersion < 2) {
            this.update(
                    "DELETE FROM sessions"
            );
            this.update(
                    "ALTER TABLE sessions ADD COLUMN username text DEFAULT NULL"
            );
        }

        if (schemaVersion < 3) {
            this.update(
                    "DELETE FROM sessions"
            );
            this.update(
                    "ALTER TABLE sessions ADD COLUMN ip text NOT NULL DEFAULT 'unknown'"
            );
            this.update(
                    "ALTER TABLE sessions RENAME COLUMN expires TO created_at"
            );
        }

        this.update(
                "UPDATE meta SET value = ? WHERE key = ?",
                Integer.toString(codeSchemaVersion), "schema"
        );

        this.update("DELETE FROM sessions WHERE created_at < ?", getUnixTime() - sessionLength);
    }

    public static class Session {
        public String sessionId;
        public String authToken;
        public long createdAt;
        @Nullable
        public String playerUuid;
        @Nullable
        public String username;
        @NotNull
        public String ip;
        public Session(@NotNull String sessionId, @NotNull String authToken, long createdAt, @Nullable String playerUuid, @Nullable String username, @NotNull String ip) {
            this.sessionId = sessionId;
            this.authToken = authToken;
            this.createdAt = createdAt;
            this.playerUuid = playerUuid;
            this.username = username;
            this.ip = ip;
        }
    }

    private static long getUnixTime() {
        return System.currentTimeMillis() / 1000L;
    }

    private static String generateNewSessionId() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    private static String generateNewAuthToken(int length) {
        return new Random().ints(48, 90 + 1)
                .filter(i -> i <= 57 || i >= 65)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public Session createSession(String ip) throws SQLException {
        Session session = new Session(
                generateNewSessionId(),
                generateNewAuthToken(authTokenLength),
                getUnixTime(),
                null,
                null,
                ip
        );
        this.update("INSERT INTO sessions VALUES (?, ?, ?, ?, ?, ?)", session.sessionId, session.authToken, session.createdAt, session.playerUuid, session.username, session.ip);
        return session;
    }

    @Nullable
    public Session getSession(@NotNull String sessionId) throws SQLException {
        Session cached = sessionCache.get(sessionId);
        Long expiry = sessionCacheExpiry.get(sessionId);
        if (expiry != null && expiry < System.currentTimeMillis()) {
            sessionCache.remove(sessionId);
            sessionCacheExpiry.remove(sessionId);
            cached = null;
        }
        if (cached != null) return cached;
        ResultSet res = this.query("SELECT auth_token, created_at, player_uuid, username, ip FROM sessions WHERE session_id = ?", sessionId);
        if (!res.next()) return null;
        String authToken = res.getString("auth_token");
        long createdAt = res.getLong("created_at");
        String uuid = res.getString("player_uuid");
        String username = res.getString("username");
        String ip = res.getString("ip");
        if (createdAt < getUnixTime() - sessionLength) {
            this.deleteSession(sessionId);
            return null;
        }
        Session session = new Session(sessionId, authToken, createdAt, uuid, username, ip);
        sessionCache.put(sessionId, session);
        sessionCacheExpiry.put(sessionId, System.currentTimeMillis() + CACHE_SECONDS * 1000);
        return session;
    }

    public void deleteSession(@NotNull String sessionId) throws SQLException {
        this.update("DELETE FROM sessions WHERE session_id = ?", sessionId);
        this.evictFromCache(sessionId);
    }

    public void deleteAllSessions(@NotNull String uuid) throws SQLException {
        ResultSet res = this.query("SELECT session_id FROM sessions WHERE player_uuid = ?", uuid);
        while (res.next()) {
            this.deleteSession(res.getString("session_id"));
        }
    }

    public int getSessionsCount(@NotNull String uuid) throws SQLException {
        ResultSet res = this.query("SELECT COUNT(*) FROM sessions WHERE player_uuid = ?", uuid);
        if (!res.next()) return 0;
        return res.getInt(1);
    }

    public void deleteOldestSessions(@NotNull String uuid, int limit) throws SQLException {
        ResultSet res = this.query("SELECT session_id FROM sessions WHERE player_uuid = ? ORDER BY created_at ASC LIMIT ?", uuid, limit);
        while (res.next()) {
            this.deleteSession(res.getString("session_id"));
        }
    }

    public void evictFromCache(@NotNull String sessionId) {
        sessionCache.remove(sessionId);
        sessionCacheExpiry.remove(sessionId);
    }

    /**
     * Returns `true` if verification was successful, `false` otherwise
     */
    public boolean verifySession(@NotNull String authToken, @NotNull String uuid, @NotNull String username) throws SQLException {
        ResultSet existingRes = this.query("SELECT * FROM sessions WHERE auth_token = ?", authToken);
        if (!existingRes.next()) return false;
        if (existingRes.getString("player_uuid") != null) return false;
        this.update("UPDATE sessions SET player_uuid = ?, username = ? WHERE auth_token = ?", uuid, username, authToken);
        String sessionId = existingRes.getString("session_id");
        sessionCache.remove(sessionId);
        sessionCacheExpiry.remove(sessionId);
        return true;
    }
}
