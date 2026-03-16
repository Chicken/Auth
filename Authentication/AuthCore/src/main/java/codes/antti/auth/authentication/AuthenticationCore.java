package codes.antti.auth.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

        try {
            this.db = new AuthenticationDatabase(adapter);
        } catch (SQLException e) {
            adapter.getLogger().severe("Failed to open database");
            e.printStackTrace();
            return;
        }

        try {
            this.server = new AuthenticationWebServer(adapter, this.db);
        } catch (IOException e) {
            adapter.getLogger().severe("Failed to create HTTP server");
            e.printStackTrace();
            return;
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
