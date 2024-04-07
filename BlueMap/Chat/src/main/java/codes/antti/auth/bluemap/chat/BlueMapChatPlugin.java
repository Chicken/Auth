package codes.antti.auth.bluemap.chat;

import codes.antti.auth.common.http.SSERequest;
import codes.antti.auth.common.http.WebServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

public final class BlueMapChatPlugin extends JavaPlugin implements Listener {
	private Configuration config;
	private WebServer http;
	private final ConcurrentHashMap<UUID, SSERequest> sessions = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> pingSchedule;


	public interface SessionOperator {
		void apply(SSERequest sse);
	}

	public void forEachSession(SessionOperator op) {
		for (SSERequest sse : sessions.values()) {
			op.apply(sse);
		}
	}

	@Override
	public void onEnable() {
		pingSchedule = scheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {
				forEachSession(SSERequest::ping);
			}
		}, 0, 30, TimeUnit.SECONDS);

		getServer().getPluginManager().registerEvents(this, this);

		BlueMapAPI.onEnable(api -> {
			reloadConfig();
			saveDefaultConfig();
			this.config = getConfig();

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
				SSERequest sse = request.sse(() -> {
					this.sessions.remove(uuid);
				});
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
				JsonElement body = request.getBodyJson();
				if (!body.isJsonObject()) {
					request.respond(400);
					return;
				}
				JsonElement message = body.getAsJsonObject().get("message");
				if (!message.isJsonPrimitive() || !message.getAsJsonPrimitive().isString()) {
					request.respond(400);
					return;
				}
				String messageString = message.getAsString();
				if (messageString.length() > 256) {
					request.respond(400);
					return;
				}
				getServer().broadcastMessage(String.format("[web] <%s> %s", username, messageString));
				JsonObject messageObject = new JsonObject();
				messageObject.addProperty("type", "webchat");
				messageObject.addProperty("uuid", uuid);
				messageObject.addProperty("username", username);
				messageObject.addProperty("message", messageString);
				forEachSession(sse -> sse.send(messageObject));
				request.respond(200);
			});

			this.http.start();
			getLogger().info("Webserver bound to " + this.http.getAddress());

			try {
				copyResource("bluemap-chat.js");
				copyResource("bluemap-chat.css");
				copyResource("minecraft.otf");
				api.getWebApp().registerScript("assets/bluemap-chat.js");
				api.getWebApp().registerStyle("assets/bluemap-chat.css");
			} catch (IOException ex) {
				getLogger().severe("Couldn't move chat resources to BlueMap!");
				ex.printStackTrace();
			}
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
		JsonObject message = new JsonObject();
		message.addProperty("type", "chat");
		message.addProperty("uuid", event.getPlayer().getUniqueId().toString());
		message.addProperty("username", event.getPlayer().getName());
		message.addProperty("message", event.getMessage());
		forEachSession(sse -> sse.send(message));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		if (event.getEntityType() != EntityType.PLAYER) return;
		JsonObject message = new JsonObject();
		message.addProperty("type", "death");
		message.addProperty("uuid", event.getEntity().getUniqueId().toString());
		message.addProperty("username", event.getEntity().getName());
		message.addProperty("message", event.getDeathMessage());
		forEachSession(sse -> sse.send(message));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "join");
		message.addProperty("uuid", event.getPlayer().getUniqueId().toString());
		message.addProperty("username", event.getPlayer().getName());
		forEachSession(sse -> sse.send(message));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "quit");
		message.addProperty("uuid", event.getPlayer().getUniqueId().toString());
		message.addProperty("username", event.getPlayer().getName());
		forEachSession(sse -> sse.send(message));
	}
}
