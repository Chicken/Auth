package codes.antti.auth.common.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Request {
    private final HttpExchange httpExchange;
    private final HashMap<String, String> cookies = new HashMap<>();
    private final ArrayList<String> responseCookies = new ArrayList<>();
    @Nullable
    private String bodyType = null;
    @Nullable
    private String body = null;
    private static final Gson gson = new Gson();
    public Request(HttpExchange httpExchange) {
        this.httpExchange = httpExchange;

        List<String> cookieHeaders = this.httpExchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String cookieHeader : cookieHeaders) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    String[] parts = cookie.trim().split("=");
                    if (parts.length == 2) this.cookies.put(parts[0], parts[1]);
                }
            }
        }
    }

    public void json(@NotNull JsonObject object) {
        this.setBody(gson.toJson(object), "application/json");
    }

    public void setBody(@NotNull String body) {
        this.setBody(body, null);
    }

    public void setBody(@NotNull String body, @Nullable String bodyType) {
        this.bodyType = bodyType;
        this.body = body;
    }

    public void respond(int statusCode) throws IOException {
        if (this.responseCookies.size() > 0) setHeader("Set-Cookie", String.join("; ", responseCookies));
        if (this.body != null) this.setHeader("Content-Type", Optional.ofNullable(bodyType).orElse("text/plain") + "; charset=UTF-8");
        if (this.body != null && this.getMethod().equals("GET")) {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            httpExchange.sendResponseHeaders(statusCode, response.length);
            OutputStream out = httpExchange.getResponseBody();
            out.write(response);
            out.close();
        } else {
            this.httpExchange.sendResponseHeaders(statusCode, -1);
        }
    }

    public void redirect(@NotNull String url) throws IOException {
        this.redirect(302, url);
    }
    public void redirect(int statusCode, @NotNull String url) throws IOException {
        this.setHeader("Location", url);
        this.respond(statusCode);
    }

    public void setHeader(@NotNull String key, @NotNull String value) {
        this.httpExchange.getResponseHeaders().set(key ,value);
    }

    public @Nullable String getHeader(@NotNull String key) {
        List<String> headers = this.httpExchange.getRequestHeaders().get(key);
        if (headers != null && headers.size() != 0) return headers.get(0);
        else return null;
    }

    public String getPath() {
        return this.httpExchange.getRequestURI().getPath();
    }

    private String formatCookie(@NotNull String name, @NotNull String value, long expires) {
        return name + "=" + value + "; " +
                "Expires=" + new Date(expires * 1000L) + "; " +
                "HttpOnly; Secure; " +
                "SameSite=Strict; " +
                "Path=/";
    }

    public HashMap<String, String> getCookies() {
        return cookies;
    }

    public String getMethod() {
        return this.httpExchange.getRequestMethod();
    }

    public void setCookie(@NotNull String key, @NotNull String value, long expires) {
        responseCookies.add(formatCookie(key, value, expires));
    }

    public void clearCookie(@NotNull String key) {
        responseCookies.add(formatCookie(key, "", 0L));
    }
}
