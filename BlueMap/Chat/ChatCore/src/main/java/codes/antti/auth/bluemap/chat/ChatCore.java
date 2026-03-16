package codes.antti.auth.bluemap.chat;

import codes.antti.auth.common.http.Required;
import codes.antti.auth.common.http.SSERequest;
import codes.antti.auth.common.http.WebServer;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.api.BlueMapAPI;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ChatCore {
    public record MessageRequest(@Required String message) {}

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @NotNull String type;
        @NotNull String uuid;
        @NotNull String username;
        String message;
    }

    private WebServer http;
    private final ConcurrentHashMap<UUID, SSERequest> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> pingSchedule;

    public void onStart() {
        this.pingSchedule = scheduler.scheduleAtFixedRate(this::pingAll, 0, 30, TimeUnit.SECONDS);
    }

    public void onBlueMapEnabled(@NotNull ChatPlatformAdapter adapter, @NotNull BlueMapAPI api) {
        try {
            api.getWebApp().registerScript("assets/bluemap-chat.js");
            api.getWebApp().registerStyle("assets/bluemap-chat.css");

            copyResource(api, "bluemap-chat.css");
            copyResource(api, "minecraft.otf");

            String integration;
            try (InputStream in = Objects.requireNonNull(
                ChatCore.class.getResourceAsStream("/assets/bluemap-chat/bluemap-chat.js"),
                "bluemap-chat.js not found in classpath"
            )) {
                integration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            integration = integration
                .replaceAll("\\{\\{web-chat-prefix}}", adapter.getWebChatPrefix())
                .replaceAll("\\{\\{max-message-count}}", String.valueOf(adapter.getMaxMessageCount()))
                .replaceAll("\\{\\{auth-path}}", adapter.getAuthPath());

            Path chatPath = api.getWebApp().getWebRoot().resolve("assets/bluemap-chat.js");
            Files.createDirectories(chatPath.getParent());
            try (OutputStream out = Files.newOutputStream(chatPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                out.write(integration.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            adapter.getLogger().severe("Couldn't move chat resources to BlueMap!");
            ex.printStackTrace();
        }

        try {
            this.http = new WebServer(adapter.getIp(), adapter.getPort());
        } catch (IOException ex) {
            adapter.getLogger().severe("Failed to create HTTP server");
            ex.printStackTrace();
            return;
        }

        this.http.get("/stream", request -> {
            if (adapter.isReadAuth()) {
                String loggedIn = request.getHeader("x-minecraft-loggedin");
                if (loggedIn == null || !loggedIn.equals("true")) {
                    request.respond(403);
                    return;
                }
            }
            UUID uuid = UUID.randomUUID();
            SSERequest sse = request.sse(() -> this.sessions.remove(uuid));
            sse.ping();
            JsonObject settingsObject = new JsonObject();
            settingsObject.addProperty("type", "settings");
            settingsObject.addProperty("readOnly", adapter.isReadOnly());
            sse.send(settingsObject);
            this.sessions.put(uuid, sse);
        });

        this.http.post("/send", request -> {
            if (adapter.isReadOnly()) {
                request.respond(405);
                return;
            }
            String loggedIn = request.getHeader("x-minecraft-loggedin");
            String uuid = request.getHeader("x-minecraft-uuid");
            String username = request.getHeader("x-minecraft-username");
            if (loggedIn == null || (loggedIn.equals("true") && (uuid == null || username == null))) {
                request.respond(400);
                return;
            }
            if (loggedIn.equals("false")) {
                request.respond(401);
                return;
            }
            assert uuid != null;
            assert username != null;
            MessageRequest body = request.getBodyAs(MessageRequest.class);
            if (body.message().length() > 256) {
                request.respond(400);
                return;
            }
            adapter.broadcastToServer(String.format("%s <%s> %s", adapter.getWebChatPrefix(), username, body.message()));
            forEachSession(sse -> sse.send(new Message("webchat", uuid, username, body.message())));
            request.respond(200);
        });

        this.http.start();
        adapter.getLogger().info("Webserver bound to " + this.http.getAddress());
    }

    public void onBlueMapDisabled() {
        if (this.http != null) this.http.close();
    }

    public void onStop() {
        if (this.pingSchedule != null) this.pingSchedule.cancel(false);
        scheduler.shutdown();
    }

    private void pingAll() {
        forEachSession(SSERequest::ping);
    }

    public void forEachSession(java.util.function.Consumer<SSERequest> op) {
        for (SSERequest sse : sessions.values()) {
            op.accept(sse);
        }
    }

    public void onChat(UUID uuid, String name, String message) {
        forEachSession(sse -> sse.send(new Message("chat", uuid.toString(), name, message)));
    }

    public void onSayCommand(String message) {
        forEachSession(sse -> sse.send(new Message("chat", "0", "[Server]", message)));
    }

    public void onPlayerSayCommand(UUID uuid, String name, String message) {
        forEachSession(sse -> sse.send(new Message("chat", uuid.toString(), name, message)));
    }

    public void onDeath(UUID uuid, String name, String deathMessage) {
        forEachSession(sse -> sse.send(new Message("death", uuid.toString(), name, deathMessage)));
    }

    public void onJoin(UUID uuid, String name) {
        forEachSession(sse -> sse.send(new Message("join", uuid.toString(), name)));
    }

    public void onQuit(UUID uuid, String name) {
        forEachSession(sse -> sse.send(new Message("quit", uuid.toString(), name)));
    }

    private void copyResource(BlueMapAPI api, String name) throws IOException {
        Path targetPath = api.getWebApp().getWebRoot().resolve("assets").resolve(name);
        Files.createDirectories(targetPath.getParent());
        try (InputStream in = Objects.requireNonNull(ChatCore.class.getResourceAsStream("/assets/bluemap-chat/" + name));
             OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
    }
}
