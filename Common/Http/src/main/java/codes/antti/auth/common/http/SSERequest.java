package codes.antti.auth.common.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;

public class SSERequest {
    private final HttpExchange httpExchange;
    private final OutputStream out;
    private final CloseHandler closeHandler;

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
        try {
            this.out.write(data.getBytes());
            this.out.flush();
        } catch (IOException e) {
            closeHandler.apply();
            try {
                httpExchange.close();
            } catch (Exception ignored) {}
        }
    }

    public void send(String data) {
        write("data: " + data + "\n\n");
    }

    public void send(JsonElement object) {
        send(object.toString());
    }

    public void ping() {
        write(":ping\n\n");
    }
}
