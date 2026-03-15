package codes.antti.auth.common.http;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

public final class WebServer {
    private final HttpServer internal;

    public WebServer(@NotNull String ip, int port) throws IOException {
        this.internal = HttpServer.create(new InetSocketAddress(ip, port), 0);
        this.internal.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.internal.createContext("/", httpExchange -> {
            httpExchange.sendResponseHeaders(404, -1);
            httpExchange.close();
        });
    }

    @FunctionalInterface
    public interface Handler {
        void apply(Request request) throws Exception;
    }

    public void get(@NotNull String path, @NotNull Handler handler) {
        this.genericContextCreator(List.of("GET", "HEAD"), path, handler);
    }

    public void post(@NotNull String path, @NotNull Handler handler) {
        this.genericContextCreator(List.of("POST"), path, handler);
    }

    private void genericContextCreator(@NotNull List<String> methods, @NotNull String path, @NotNull Handler handler) {
        this.internal.createContext(path.endsWith("*") ? path.substring(0, path.length() - 1) : path, httpExchange -> {
            Request request = new Request(httpExchange);
            if (!methods.contains(request.getMethod())) {
                request.respond(400);
                return;
            }
            String requestPath = request.getPath();
            if (!path.endsWith("*") && !path.equals(requestPath)) {
                request.respond(404);
                return;
            }
            try {
                handler.apply(request);
            } catch (HTTPError err) {
                if (!request.isResponded()) {
                    try {
                        request.setBody(err.getMessage());
                        request.respond(err.getStatusCode());
                    } catch (Exception ignored) {}
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                if (!request.isResponded()) {
                    try {
                        request.respond(500);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public void serveStatic(@NotNull String path, @NotNull Path root) {
        String rootString = root.toFile().getAbsolutePath();
        if (!path.startsWith("/")) throw new RuntimeException("Path should start with a slash");
        if (!path.endsWith("/")) throw new RuntimeException("Path should end with a slash");
        this.internal.createContext(path, httpExchange -> {
            Request request = new Request(httpExchange);
            String method = request.getMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                request.respond(400);
                return;
            }
            String wholePath = request.getPath();
            if (wholePath.endsWith("/")) wholePath += "index.html";
            String fsPath = wholePath.substring(path.length());
            File file;
            try {
                file = root.resolve(fsPath).toFile().getCanonicalFile();
            } catch (IOException ex) {
                request.respond(500);
                return;
            }
            if (!file.getPath().startsWith(rootString)) {
                request.respond(400);
                return;
            }
            try (FileInputStream in = new FileInputStream(file)) {
                String mimeType;
                try {
                    mimeType = Optional.ofNullable(Files.probeContentType(file.toPath())).orElse("text/plain");
                } catch (Exception ignored) {
                    mimeType = "text/plain";
                }
                httpExchange.getResponseHeaders().set("Content-Type", mimeType);
                httpExchange.sendResponseHeaders(200, file.length());
                if (method.equals("GET")) {
                    OutputStream out = httpExchange.getResponseBody();
                    in.transferTo(out);
                    out.flush();
                    out.close();
                }
                httpExchange.close();
            } catch (FileNotFoundException e) {
                request.respond(404);
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
        this.internal.stop(1);
    }
}
