package codes.antti.auth.common.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URLDecoder;
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

    public void setBody(@NotNull String body, @Nullable String bodyType) {
        this.bodyType = bodyType;
        this.body = body;
    }

    public String getBodyString() throws IOException {
        return new String(this.httpExchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public JsonElement getBodyJson() throws IOException {
        return gson.fromJson(getBodyString(), JsonElement.class);
    }

    public void respond(int statusCode) throws IOException {
        if (!this.responseCookies.isEmpty()) setHeader("Set-Cookie", String.join("; ", responseCookies));
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
        if (headers != null && !headers.isEmpty()) return headers.get(0);
        else return null;
    }

    public String getPath() {
        return this.httpExchange.getRequestURI().getPath();
    }

    public Map<String, String> getQuery() {
        String query = this.httpExchange.getRequestURI().getQuery();
        if (query == null) return new HashMap<>();
        Map<String, String> result = new HashMap<>();
        for (String part : query.split("&")) {
            String[] parts = part.split("=");
            if (parts.length == 2) result.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
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

    public SSERequest sse(SSERequest.CloseHandler closeHandler) throws IOException {
        return new SSERequest(this.httpExchange, closeHandler);
    }

    public String getProxyIp() {
        String forwardedFor = this.getHeader("x-forwarded-for");
        if (forwardedFor == null) return "unknown";
        String address = forwardedFor.split(",")[0];
        try {
            if (address.contains(":")) {
                return String.join(":", Arrays.copyOfRange(InetAddress.getByName(address).getHostAddress().split(":"), 0, 4)) + "::/64";
            } else {
                return address;
            }
        } catch (Exception e) {
            return "unknown";
        }
    }
}
