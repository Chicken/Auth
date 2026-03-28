package codes.antti.auth.authentication;

import codes.antti.auth.common.database.Database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class AuthenticationDatabase {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();
    private final Database db;
    private final ConcurrentHashMap<String, Session> sessionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionCacheExpiry = new ConcurrentHashMap<>();
    private static final long CACHE_SECONDS = 5;
    private final int authTokenLength;
    public final long sessionLength;

    public AuthenticationDatabase(@NotNull Database db, int authTokenLength, long sessionLength) throws SQLException {
        this.db = db;
        this.authTokenLength = authTokenLength;
        this.sessionLength = sessionLength;

        this.db.update("""
            CREATE TABLE IF NOT EXISTS meta (
                `key` VARCHAR(255) NOT NULL,
                value VARCHAR(255) DEFAULT NULL,
                PRIMARY KEY (`key`)
            )
            """);

        Integer schemaVersion = this.db.query(
            "SELECT value FROM meta WHERE `key` = 'schema'",
            rs -> {
                if (!rs.next()) return null;
                try {
                    return Integer.parseInt(rs.getString("value"));
                } catch (NumberFormatException e) {
                    throw new SQLException("Invalid schema version");
                }
            }
        );

        int codeSchemaVersion = 3;

        if (schemaVersion == null) {
            this.db.update("""
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id VARCHAR(255) PRIMARY KEY NOT NULL,
                    auth_token VARCHAR(255) UNIQUE NOT NULL,
                    created_at BIGINT NOT NULL,
                    player_uuid VARCHAR(255) DEFAULT NULL,
                    username VARCHAR(255) DEFAULT NULL,
                    ip VARCHAR(255) NOT NULL DEFAULT 'unknown'
                )
                """);
            this.db.update("INSERT INTO meta VALUES (?, ?)", "schema", Integer.toString(codeSchemaVersion));
        } else {
            if (schemaVersion < 0 || schemaVersion > codeSchemaVersion) throw new SQLException("Invalid schema version");

            if (schemaVersion < 1) {
                this.db.update("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        session_id VARCHAR(255) PRIMARY KEY NOT NULL,
                        auth_token VARCHAR(255) UNIQUE NOT NULL,
                        expires integer NOT NULL,
                        player_uuid VARCHAR(255) DEFAULT NULL
                    )
                    """);
            }

            if (schemaVersion < 2) {
                this.db.update("DELETE FROM sessions");
                this.db.update("ALTER TABLE sessions ADD COLUMN username VARCHAR(255) DEFAULT NULL");
            }

            if (schemaVersion < 3) {
                this.db.update("DELETE FROM sessions");
                this.db.update("ALTER TABLE sessions ADD COLUMN ip VARCHAR(255) NOT NULL DEFAULT 'unknown'");
                this.db.update("ALTER TABLE sessions RENAME COLUMN expires TO created_at");
            }

            this.db.update(
                "UPDATE meta SET value = ? WHERE `key` = ?",
                Integer.toString(codeSchemaVersion), "schema"
            );
        }

        this.db.update("DELETE FROM sessions WHERE created_at < ?", getUnixTime() - sessionLength);
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
        return secureRandom.ints(48, 90 + 1)
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
        this.db.update("INSERT INTO sessions VALUES (?, ?, ?, ?, ?, ?)", session.sessionId, session.authToken, session.createdAt, session.playerUuid, session.username, session.ip);
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

        Session session = this.db.query(
            "SELECT auth_token, created_at, player_uuid, username, ip FROM sessions WHERE session_id = ?",
            rs -> {
                if (!rs.next()) return null;
                return new Session(sessionId, rs.getString("auth_token"), rs.getLong("created_at"), rs.getString("player_uuid"), rs.getString("username"), rs.getString("ip"));
            },
            sessionId
        );

        if (session == null) return null;
        if (session.createdAt < getUnixTime() - sessionLength) {
            this.deleteSession(sessionId);
            return null;
        }
        sessionCache.put(sessionId, session);
        sessionCacheExpiry.put(sessionId, System.currentTimeMillis() + CACHE_SECONDS * 1000);
        return session;
    }

    public void deleteSession(@NotNull String sessionId) throws SQLException {
        this.db.update("DELETE FROM sessions WHERE session_id = ?", sessionId);
        this.evictFromCache(sessionId);
    }

    public void deleteAllSessions(@NotNull String uuid) throws SQLException {
        List<String> sessionIds = this.db.query(
            "SELECT session_id FROM sessions WHERE player_uuid = ?",
            rs -> {
                List<String> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getString("session_id"));
                return ids;
            },
            uuid
        );
        for (String id : sessionIds) {
            this.deleteSession(id);
        }
    }

    public int getSessionsCount(@NotNull String uuid) throws SQLException {
        return this.db.query(
            "SELECT COUNT(*) FROM sessions WHERE player_uuid = ?",
            rs -> rs.next() ? rs.getInt(1) : 0,
            uuid
        );
    }

    public void deleteOldestSessions(@NotNull String uuid, int limit) throws SQLException {
        List<String> sessionIds = this.db.query(
            "SELECT session_id FROM sessions WHERE player_uuid = ? ORDER BY created_at ASC LIMIT ?",
            rs -> {
                List<String> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getString("session_id"));
                return ids;
            },
            uuid, limit
        );
        for (String id : sessionIds) {
            this.deleteSession(id);
        }
    }

    public void evictFromCache(@NotNull String sessionId) {
        sessionCache.remove(sessionId);
        sessionCacheExpiry.remove(sessionId);
    }

    /**
     * Returns {@code true} if verification was successful, {@code false} otherwise
     */
    public boolean verifySession(@NotNull String authToken, @NotNull String uuid, @NotNull String username) throws SQLException {
        String sessionId = this.db.query(
            "SELECT session_id FROM sessions WHERE auth_token = ? AND player_uuid IS NULL",
            rs -> rs.next() ? rs.getString("session_id") : null,
            authToken
        );
        if (sessionId == null) return false;
        this.db.update("UPDATE sessions SET player_uuid = ?, username = ? WHERE auth_token = ?", uuid, username, authToken);
        sessionCache.remove(sessionId);
        sessionCacheExpiry.remove(sessionId);
        return true;
    }

    public void close() {
        db.close();
    }
}
