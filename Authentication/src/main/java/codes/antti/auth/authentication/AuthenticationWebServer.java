package codes.antti.auth.authentication;

import codes.antti.auth.common.http.WebServer;
import codes.antti.auth.authentication.AuthenticationDatabase.Session;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class AuthenticationWebServer {
    private static final String SESSION_ID_COOKIE = "mc_auth_sid";
    private static final String X_LOGGEDIN_HEADER = "x-minecraft-loggedin";
    private static final String X_UUID_HEADER = "x-minecraft-uuid";
    private static final String X_USERNAME_HEADER = "x-minecraft-username";


    private final WebServer http;
    private final AuthenticationPlugin plugin;
    private final Path loginPagePath;

    public AuthenticationWebServer(@NotNull AuthenticationPlugin plugin) throws IOException {
        this.plugin = plugin;
        this.loginPagePath = this.plugin.getDataFolder().toPath().resolve("web/login.html");
        FileConfiguration config = plugin.getConfig();
        this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8200));
        final String root = "/";
        final boolean optionalAuth = config.getBoolean("optional_authentication", false);



        this.http.get("/auth", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            if (sessionId == null) {
                if (optionalAuth) {
                    request.setHeader(X_LOGGEDIN_HEADER, "false");
                    request.respond(200);
                }
                else request.respond(401);
                return;
            }
            Session session = plugin.db.getSession(sessionId);
            if (session == null || session.playerUuid == null || session.username == null) {
                if (optionalAuth) {
                    request.setHeader(X_LOGGEDIN_HEADER, "false");
                    request.respond(200);
                }
                else request.respond(401);
                return;
            }
            request.setHeader(X_LOGGEDIN_HEADER, "true");
            request.setHeader(X_UUID_HEADER, session.playerUuid);
            request.setHeader(X_USERNAME_HEADER, session.username);
            request.respond(200);
        });



        this.http.get("/login", request -> {
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

        this.http.serveStatic("/login/", this.plugin.getDataFolder().toPath().resolve("web"));



        this.http.get("/logout", request -> {
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



        this.http.get("/logout/all", request -> {
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
        String loginPage;
        try {
            loginPage = Files.readString(this.loginPagePath);
        } catch (IOException e) {
            loginPage = "Authenticate using: /auth {{auth_token}}";
            this.plugin.getLogger().severe("Couldn't load login page, using a plain one");
            e.printStackTrace();
        }
        return loginPage.replaceAll("\\{\\{auth_token}}", authToken);
    }

    public void close() {
        this.http.close();
    }
}

