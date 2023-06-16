package codes.antti.auth.common.http;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
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

    @FunctionalInterface
    public interface Handler {
        void apply(Request request) throws Exception;
    }

    public void get(@NotNull String path, @NotNull Handler handler) {
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
