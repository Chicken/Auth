package codes.antti.auth.authentication;

import codes.antti.auth.common.http.WebServer;
import codes.antti.auth.authentication.AuthenticationDatabase.Session;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

public class AuthenticationWebServer {
    private static final String SESSION_ID_COOKIE = "mc_auth_sid";
    private static final String X_LOGGEDIN_HEADER = "x-minecraft-loggedin";
    private static final String X_UUID_HEADER = "x-minecraft-uuid";
    private static final String X_USERNAME_HEADER = "x-minecraft-username";

    private final WebServer http;
    private final AuthenticationDatabase db;
    private final Path loginPagePath;
    private final Logger logger;

    public AuthenticationWebServer(@NotNull AuthPlatformAdapter adapter, @NotNull AuthenticationDatabase db) throws IOException {
        this.db = db;
        this.logger = adapter.getLogger();
        this.loginPagePath = adapter.getDataPath().resolve("web/login.html");
        final boolean ipLock = adapter.isIpLock();
        this.http = new WebServer(adapter.getIp(), adapter.getPort());
        final boolean optionalAuth = adapter.isOptionalAuthentication();


        this.http.get("/auth", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            if (sessionId == null) {
                if (optionalAuth) {
                    request.setHeader(X_LOGGEDIN_HEADER, "false");
                    request.respond(200);
                } else request.respond(401);
                return;
            }
            Session session = this.db.getSession(sessionId);
            if (session == null || session.playerUuid == null || session.username == null) {
                if (optionalAuth) {
                    request.setHeader(X_LOGGEDIN_HEADER, "false");
                    request.respond(200);
                } else request.respond(401);
                return;
            }
            if (!request.getProxyIp().equals(session.ip) && ipLock) {
                this.db.deleteSession(sessionId);
                if (optionalAuth) {
                    request.setHeader(X_LOGGEDIN_HEADER, "false");
                    request.respond(200);
                } else request.respond(401);
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
                session = this.db.createSession(request.getProxyIp());
                request.setCookie(SESSION_ID_COOKIE, session.sessionId, session.createdAt + this.db.sessionLength);
                request.setBody(formatLoginPage(session.authToken), "text/html");
                request.respond(200);
                return;
            }

            session = this.db.getSession(sessionId);
            if (session != null) {
                if (session.playerUuid != null) {
                    String redirectUri = Optional.ofNullable(request.getQuery().get("redirect")).orElse("/");
                    request.redirect(redirectUri);
                    return;
                }
                request.setBody(formatLoginPage(session.authToken), "text/html");
                request.respond(200);
                return;
            }

            session = this.db.createSession(request.getProxyIp());
            request.setCookie(SESSION_ID_COOKIE, session.sessionId, session.createdAt + this.db.sessionLength);
            request.setBody(formatLoginPage(session.authToken), "text/html");
            request.respond(200);
        });

        this.http.serveStatic("/login/", adapter.getDataPath().resolve("web"));


        this.http.get("/logout", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            String redirectUri = Optional.ofNullable(request.getQuery().get("redirect")).orElse("/");

            if (sessionId == null) {
                request.redirect(redirectUri);
                return;
            }
            Session session = this.db.getSession(sessionId);
            if (session == null || session.playerUuid == null) {
                request.redirect(redirectUri);
                return;
            }
            this.db.deleteSession(sessionId);
            request.clearCookie(SESSION_ID_COOKIE);
            request.redirect(redirectUri);
        });


        this.http.get("/logout/all", request -> {
            String sessionId = request.getCookies().get(SESSION_ID_COOKIE);
            String redirectUri = Optional.ofNullable(request.getQuery().get("redirect")).orElse("/");

            if (sessionId == null) {
                request.redirect(redirectUri);
                return;
            }
            Session session = this.db.getSession(sessionId);
            if (session == null || session.playerUuid == null) {
                request.redirect(redirectUri);
                return;
            }
            this.db.deleteAllSessions(session.playerUuid);
            request.clearCookie(SESSION_ID_COOKIE);
            request.redirect(redirectUri);
        });


        this.http.start();
        this.logger.info("Webserver bound to " + this.http.getAddress());
    }

    private String formatLoginPage(@NotNull String authToken) {
        String loginPage;
        try {
            loginPage = Files.readString(this.loginPagePath);
        } catch (IOException e) {
            loginPage = "Authenticate using: /auth {{auth_token}}";
            this.logger.severe("Couldn't load login page, using a plain one");
            e.printStackTrace();
        }
        return loginPage.replaceAll("\\{\\{auth_token}}", authToken);
    }

    public void close() {
        this.http.close();
    }
}
