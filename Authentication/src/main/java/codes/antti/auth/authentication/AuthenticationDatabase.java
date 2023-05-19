package codes.antti.auth.authentication;

import codes.antti.auth.common.Database;
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
    private static final long CACHE_SECONDS = 15;

    public AuthenticationDatabase(@NotNull AuthenticationPlugin plugin) throws SQLException {
        super(plugin.getDataFolder().getAbsolutePath() + "/db.sqlite");
        this.plugin = plugin;

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

        int codeSchemaVersion = 1;
        if (schemaVersion < 0 || schemaVersion > codeSchemaVersion) throw new SQLException("Invalid schema version");

        if (schemaVersion == 0) {
            this.update(
                    "CREATE TABLE IF NOT EXISTS sessions (" +
                            "session_id text PRIMARY KEY NOT NULL," +
                            "auth_token text UNIQUE NOT NULL," +
                            "expires integer NOT NULL," +
                            "player_uuid text DEFAULT NULL" +
                            ")"
            );

            this.update(
                    "UPDATE meta SET value = ? WHERE key = ?",
                    Integer.toString(codeSchemaVersion), "schema"
            );
        }

        this.update("DELETE FROM sessions WHERE expires < ?", getUnixTime());
    }

    public static class Session {
        public String sessionId;
        public String authToken;
        public long expires;
        @Nullable
        public String playerUuid;
        public Session(@NotNull String sessionId, @NotNull String authToken, long expires, @Nullable String playerUuid) {
            this.sessionId = sessionId;
            this.authToken = authToken;
            this.expires = expires;
            this.playerUuid = playerUuid;
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

    public Session createSession() throws SQLException {
        Session session = new Session(
                generateNewSessionId(),
                generateNewAuthToken(plugin.getConfig().getInt("auth_token_length", 7)),
                getUnixTime() + plugin.getConfig().getLong("session_length_days", 31) * 24 * 60 * 60,
                null
        );
        this.update("INSERT INTO sessions VALUES (?, ?, ?, ?)", session.sessionId, session.authToken, session.expires, session.playerUuid);
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
        ResultSet res = this.query("SELECT player_uuid, auth_token, expires FROM sessions WHERE session_id = ?", sessionId);
        if (!res.next()) return null;
        long expires = res.getLong("expires");
        String uuid = res.getString("player_uuid");
        String authToken = res.getString("auth_token");
        if (expires < getUnixTime()) {
            this.deleteSession(sessionId);
            return null;
        }
        Session session = new Session(sessionId, authToken, expires, uuid);
        sessionCache.put(sessionId, session);
        sessionCacheExpiry.put(sessionId, System.currentTimeMillis() + CACHE_SECONDS * 1000);
        return session;
    }

    public void deleteSession(@NotNull String sessionId) throws SQLException {
        this.update("DELETE FROM sessions WHERE session_id = ?", sessionId);
    }

    public void deleteAllSessions(@NotNull String uuid) throws SQLException {
        this.update("DELETE FROM sessions WHERE player_uuid = ?", uuid);
    }

    /**
     * Returns `true` if verification was successful, `false` otherwise
     */
    public boolean verifySession(@NotNull String authToken, @NotNull String uuid) throws SQLException {
        ResultSet existingRes = this.query("SELECT * FROM sessions WHERE auth_token = ?", authToken);
        if (!existingRes.next()) return false;
        if (existingRes.getString("player_uuid") != null) return false;
        this.update("UPDATE sessions SET player_uuid = ? WHERE auth_token = ?", uuid, authToken);
        return true;
    }
}
