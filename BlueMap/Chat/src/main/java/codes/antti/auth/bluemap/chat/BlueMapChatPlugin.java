package codes.antti.auth.bluemap.chat;

import codes.antti.auth.common.http.Required;
import codes.antti.auth.common.http.SSERequest;
import codes.antti.auth.common.http.WebServer;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.api.BlueMapAPI;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

public final class BlueMapChatPlugin extends JavaPlugin implements Listener {
    private Configuration config;
    private WebServer http;
    private final ConcurrentHashMap<UUID, SSERequest> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> pingSchedule;

    public record MessageRequest(
        @Required String message
    ) {
    }

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @NotNull String type;
        @NotNull String uuid;
        @NotNull String username;
        String message;
    }

    public interface SessionOperator {
        void apply(SSERequest sse);
    }

    public void forEachSession(SessionOperator op) {
        for (SSERequest sse : sessions.values()) {
            op.apply(sse);
        }
    }

    @Override
    public void onLoad() {
        BlueMapAPI.onEnable(api -> {
            reloadConfig();
            saveDefaultConfig();
            this.config = getConfig();

            try {
                api.getWebApp().registerScript("assets/bluemap-chat.js");
                api.getWebApp().registerStyle("assets/bluemap-chat.css");

                copyResource("bluemap-chat.css");
                copyResource("minecraft.otf");

                String integration = new String(Objects.requireNonNull(getResource("bluemap-chat.js")).readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\{\\{web-chat-prefix}}", Objects.requireNonNull(this.config.getString("web-chat-prefix", "[web]")))
                    .replaceAll("\\{\\{max-message-count}}", Objects.requireNonNull(this.config.getString("max-message-count", "100")));

                Optional<String> authPath = Optional.ofNullable(getServer().getPluginManager().getPlugin("BlueMap-Auth"))
                    .map(plugin -> plugin.getConfig().getString("auth-path", "/authentication-outpost/"));

                if (authPath.isPresent()) {
                    integration = integration.replaceAll("\\{\\{auth-path}}", authPath.get());
                }

                Path chatPath = api.getWebApp().getWebRoot().resolve("assets/bluemap-chat.js");
                Files.createDirectories(chatPath.getParent());
                OutputStream out = Files.newOutputStream(chatPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                out.write(integration.getBytes(StandardCharsets.UTF_8));
                out.close();
            } catch (IOException ex) {
                getLogger().severe("Couldn't move chat resources to BlueMap!");
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void onEnable() {
        pingSchedule = scheduler.scheduleAtFixedRate(() -> forEachSession(SSERequest::ping), 0, 30, TimeUnit.SECONDS);

        getServer().getPluginManager().registerEvents(this, this);

        BlueMapAPI.onEnable(api -> {
            try {
                this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8800));
            } catch (IOException ex) {
                getLogger().severe("Failed to create HTTP server");
                ex.printStackTrace();
                return;
            }

            this.http.get("/stream", request -> {
                if (this.config.getBoolean("read-auth", false)) {
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
                settingsObject.addProperty("readOnly", this.config.getBoolean("read-only", false));
                sse.send(settingsObject);
                this.sessions.put(uuid, sse);
            });

            this.http.post("/send", request -> {
                if (this.config.getBoolean("read-only", false)) {
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
                if (body.message.length() > 256) {
                    request.respond(400);
                    return;
                }
                getServer().broadcastMessage(String.format("%s <%s> %s", config.getString("web-chat-prefix", "[web]"), username, body.message));
                forEachSession(sse -> sse.send(new Message("webchat", uuid, username, body.message)));
                request.respond(200);
            });

            this.http.start();
            getLogger().info("Webserver bound to " + this.http.getAddress());
        });
        BlueMapAPI.onDisable(api -> {
            if (this.http != null) this.http.close();
        });
        getLogger().info("BlueMap Chat enabled");
    }

    public void copyResource(String name) throws IOException {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        Path targetPath = api.getWebApp().getWebRoot().resolve("assets").resolve(name);
        Files.createDirectories(targetPath.getParent());
        OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        out.write(Objects.requireNonNull(getResource(name)).readAllBytes());
        out.close();
    }

    @Override
    public void onDisable() {
        pingSchedule.cancel(false);
        scheduler.shutdown();
        getLogger().info("BlueMap Chat disabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMessage(AsyncPlayerChatEvent event) {
        forEachSession(sse -> sse.send(new Message(
            "chat",
            event.getPlayer().getUniqueId().toString(),
            event.getPlayer().getName(),
            event.getMessage()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (event.getCommand().startsWith("say ")) {
            forEachSession(sse -> sse.send(new Message(
                "chat",
                "0",
                "[Server]",
                event.getCommand().substring(4).trim()
            )));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandSend(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().startsWith("/say ")) {
            forEachSession(sse -> sse.send(new Message(
                "chat",
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                event.getMessage().substring(4).trim()
            )));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;
        forEachSession(sse -> sse.send(new Message(
            "death",
            event.getEntity().getUniqueId().toString(),
            event.getEntity().getName(),
            event.getDeathMessage()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        forEachSession(sse -> sse.send(new Message(
            "join",
            event.getPlayer().getUniqueId().toString(),
            event.getPlayer().getName()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        forEachSession(sse -> sse.send(new Message(
            "quit",
            event.getPlayer().getUniqueId().toString(),
            event.getPlayer().getName()
        )));
    }
}
