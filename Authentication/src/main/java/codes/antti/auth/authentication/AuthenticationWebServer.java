package codes.antti.auth.authentication;

import codes.antti.auth.common.WebServer;
import codes.antti.auth.authentication.AuthenticationDatabase.Session;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

public class AuthenticationWebServer {
    private static final String SESSION_ID_COOKIE = "mc_auth_sid";
    private static final String X_UUID_HEADER = "x-minecraft-uuid";

    private final WebServer http;
    private final AuthenticationPlugin plugin;
    private String loginPage = null;

    public AuthenticationWebServer(@NotNull AuthenticationPlugin plugin) throws IOException {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8200));
        final String root = "/";



        this.http.handle("/auth", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            if (sessionId == null) {
                request.respond(401);
                return;
            }
            Session session = plugin.db.getSession(sessionId);
            if (session == null || session.playerUuid == null) {
                request.respond(401);
                return;
            }
            request.setHeader(X_UUID_HEADER, session.playerUuid);
            request.respond(200);
        });



        this.http.handle("/login", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            Session session;
            if (sessionId == null) {
                session = plugin.db.createSession();
                request.setCookie(SESSION_ID_COOKIE, session.sessionId, session.expires);
                request.setBody(formatLoginPage(session.authToken), "text/html");
                request.respond(200);
                return;
            }

            session = plugin.db.getSession(sessionId);
            if (session != null) {
                if (session.playerUuid != null) {
                    request.redirect(root);
                    return;
                }
                request.setBody(formatLoginPage(session.authToken), "text/html");
                request.respond(200);
                return;
            }

            session = plugin.db.createSession();
            request.setCookie(SESSION_ID_COOKIE, session.sessionId, session.expires);
            request.setBody(formatLoginPage(session.authToken), "text/html");
            request.respond(200);
        });



        this.http.handle("/logout", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            if (sessionId == null) {
                request.redirect(root);
                return;
            }
            Session session = plugin.db.getSession(sessionId);
            if (session == null || session.playerUuid == null) {
                request.redirect(root);
                return;
            }
            plugin.db.deleteSession(sessionId);
            request.clearCookie(SESSION_ID_COOKIE);
            request.redirect(root);
        });



        this.http.handle("/logout/all", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            if (sessionId == null) {
                request.redirect(root);
                return;
            }
            Session session = plugin.db.getSession(sessionId);
            if (session == null || session.playerUuid == null) {
                request.redirect(root);
                return;
            }
            plugin.db.deleteAllSessions(session.playerUuid);
            request.clearCookie(SESSION_ID_COOKIE);
            request.redirect(root);
        });



        this.http.start();
        plugin.getLogger().info("Webserver bound to " + this.http.getAddress());
    }

    private String formatLoginPage(@NotNull String authToken) {
        if (this.loginPage == null) {
            try {
                this.loginPage = Files.readString(this.plugin.getDataFolder().toPath().resolve("login.html"));
            } catch (IOException e) {
                this.loginPage = "Authenticate using: /auth {{auth_token}}";
                this.plugin.getLogger().severe("Couldn't load login page, using a plain one");
                e.printStackTrace();
            }
        }
        return this.loginPage.replaceAll("\\{\\{auth_token}}", authToken);
    }

    public void close() {
        this.http.close();
    }
}

