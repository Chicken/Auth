package codes.antti.auth.common.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public class SSERequest {
    private final HttpExchange httpExchange;
    private final OutputStream out;
    private final CloseHandler closeHandler;
    private boolean closed = false;

    @FunctionalInterface
    public interface CloseHandler {
        void apply();
    }


    public SSERequest(HttpExchange httpExchange, CloseHandler closeHandler) throws IOException {
        this.httpExchange = httpExchange;
        this.closeHandler = closeHandler;
        Headers headers = this.httpExchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream");
        headers.set("Connection", "keep-alive");
        headers.set("Cache-Control", "no-cache");
        this.httpExchange.sendResponseHeaders(200, 0);
        this.out = httpExchange.getResponseBody();
    }

    private void write(String data) {
        if (closed) throw new IllegalStateException("Already closed!");
        try {
            this.out.write(data.getBytes());
            this.out.flush();
        } catch (IOException e) {
            close();
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        try { out.flush(); } catch (Exception ignored) {}
        try { httpExchange.close(); } catch (Exception ignored) {}
        closeHandler.apply();
    }

    public void send(@NotNull String data) {
        write("data: " + data + "\n\n");
    }

    public void send(@NotNull Object object) {
        send(Request.gson.toJson(object));
    }

    public void ping() {
        write(":ping\n\n");
    }
}
