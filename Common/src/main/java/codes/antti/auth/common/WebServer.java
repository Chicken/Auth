package codes.antti.auth.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class WebServer {
    private final HttpServer internal;
    public WebServer(@NotNull String ip, int port) throws IOException {
        this.internal = HttpServer.create(new InetSocketAddress(ip, port), 0);
        this.internal.createContext("/", httpExchange -> {
            httpExchange.sendResponseHeaders(404, -1);
        });
    }

    public static class Request {
        public final HttpExchange httpExchange;
        private final HashMap<String, String> cookies = new HashMap<>();
        private final ArrayList<String> responseCookies = new ArrayList<>();
        @Nullable
        private String bodyType = null;
        @Nullable
        private String body = null;
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

    @FunctionalInterface
    public interface Handler {
        void apply(Request request) throws Exception;
    }

    public void handle(@NotNull String path, @NotNull Handler handler) {
        this.internal.createContext(path.endsWith("*") ? path.substring(0, path.length() - 1) : path, httpExchange -> {
            String method = httpExchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                httpExchange.sendResponseHeaders(400, -1);
                return;
            }
            String requestPath = httpExchange.getRequestURI().getPath();
            if (!path.endsWith("*") && !path.equals(requestPath)) {
                httpExchange.sendResponseHeaders(404, -1);
                return;
            }
            try {
                handler.apply(new Request(httpExchange));
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public void serveStatic(@NotNull String path, @NotNull Path root) {
        String rootString = root.toFile().getAbsolutePath();
        if (!path.startsWith("/")) throw new RuntimeException("Path should start with a slash");
        if (!path.endsWith("/")) throw new RuntimeException("Path should end with a slash");
        this.internal.createContext(path, httpExchange -> {
            String method = httpExchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                httpExchange.sendResponseHeaders(400, -1);
                return;
            }
            String wholePath = httpExchange.getRequestURI().getPath();
            if (wholePath.endsWith("/")) wholePath += "index.html";
            String fsPath = wholePath.substring(path.length());
            File file;
            try {
                file = root.resolve(fsPath).toFile().getCanonicalFile();
            } catch (IOException ex) {
                httpExchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!file.getPath().startsWith(rootString)) {
                httpExchange.sendResponseHeaders(400, -1);
                return;
            }
            try (FileInputStream in = new FileInputStream(file)) {
                String mimeType;
                try {
                    mimeType = Optional.ofNullable(Files.probeContentType(file.toPath())).orElse("text/plain");
                } catch (Exception ignored) {
                    mimeType = "text/plain";
                };
                httpExchange.getResponseHeaders().set("Content-Type", mimeType);
                if (method.equals("GET")) {
                    httpExchange.sendResponseHeaders(200, file.length());
                    OutputStream out = httpExchange.getResponseBody();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                    }
                    out.close();
                } else {
                    httpExchange.sendResponseHeaders(200, -1);
                }
            } catch (FileNotFoundException e) {
                httpExchange.sendResponseHeaders(404, -1);
            }
        });
    }

    public String getAddress() {
        return this.internal.getAddress().toString();
    }

    public void start() {
        this.internal.start();
    }

    public void close() {
        this.internal.stop(3);
    }
}
