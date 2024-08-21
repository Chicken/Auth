package codes.antti.auth.bluemap.privatelocation;

import codes.antti.auth.common.http.WebServer;
import com.google.gson.stream.JsonWriter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BlueMapPrivateLocationPlugin extends JavaPlugin {
	private Configuration config;
	private WebServer http;
	private UserManager userManager;
	private final HashMap<String, Boolean> permissionCache = new HashMap<>();
	private final HashMap<String, Long> permissionCacheExpiry = new HashMap<>();
	private static final long CACHE_SECONDS = 60;
	@Override
	public void onEnable() {

		BlueMapAPI.onEnable(api -> {
			reloadConfig();
			saveDefaultConfig();
			this.config = getConfig();
			LuckPerms lp = LuckPermsProvider.get();
			this.userManager = lp.getUserManager();

			try {
				this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8500));
			} catch (IOException ex) {
				getLogger().severe("Failed to create HTTP server");
				ex.printStackTrace();
				return;
			}

			this.http.get("/players/*", request -> {
				String playerUuid = request.getHeader("x-minecraft-uuid");
				String loggedIn = request.getHeader("x-minecraft-loggedin");
				if (loggedIn == null) {
					request.respond(400);
					return;
				}
				String mapId = request.getPath().substring(9);
				Optional<BlueMapMap> map = api.getMap(mapId);
				if (map.isEmpty()) {
					request.respond(404);
					return;
				}
				if (!loggedIn.equals("true")) {
					request.setBody("{\"players\":[]}", "application/json");
					request.respond(200);
					return;
				}
				if (playerUuid == null) {
					request.respond(400);
					return;
				}
				BlueMapWorld targetWorld = map.get().getWorld();
				Optional<World> world = getServer().getWorlds().stream().filter((w) -> api.getWorld(w).map((bmw) -> bmw.equals(targetWorld)).orElse(false)).findFirst();
				if (world.isEmpty()) {
					request.respond(404);
					return;
				}
				canSeeEveryone(playerUuid).thenAcceptAsync((showAll) -> {
					String response = getPlayersObject(api, map.get(), world.get(), UUID.fromString(playerUuid), showAll);
					request.setBody(response, "application/json");
					try {
						request.respond(200);
					} catch (IOException ex) {
						getLogger().severe("Failed to send response");
						ex.printStackTrace();
					}
				});
			});

			this.http.start();
			getLogger().info("Webserver bound to " + this.http.getAddress());
		});
		BlueMapAPI.onDisable(api -> {
			if (this.http != null) this.http.close();
		});
		getLogger().info("BlueMap-PrivateLocation enabled");
	}

	public String getPlayersObject(BlueMapAPI api, BlueMapMap map, World world, UUID playerUuid, boolean showAll) {
		try (StringWriter jsonString = new StringWriter();
			 JsonWriter json = new JsonWriter(jsonString)) {

			json.beginObject();
			json.name("players").beginArray();

			Player[] players = showAll ? getServer().getOnlinePlayers().toArray(Player[]::new) : new Player[]{ this.getServer().getPlayer(playerUuid) };
			for (Player player : players) {
				if (player != null && player.isOnline()) {
					try {
						BMSkin.createPlayerHeadIfNotExists(api, map, playerUuid);
					} catch (IOException ex) {
						ex.printStackTrace();
					}

					json.beginObject();
					json.name("uuid").value(player.getUniqueId().toString());
					json.name("name").value(player.getName());
					json.name("foreign").value(!player.getWorld().equals(world));

					json.name("position").beginObject();
					json.name("x").value(player.getLocation().getX());
					json.name("y").value(player.getLocation().getY());
					json.name("z").value(player.getLocation().getZ());
					json.endObject();

					json.name("rotation").beginObject();
					json.name("pitch").value(player.getLocation().getDirection().getX());
					json.name("yaw").value(player.getLocation().getDirection().getY());
					json.name("roll").value(player.getLocation().getDirection().getZ());
					json.endObject();

					json.endObject();
				}
			}

			json.endArray();
			json.endObject();

			json.flush();
			return jsonString.toString();
		} catch (IOException ex) {
			getLogger().severe("Failed to create JSON response");
			ex.printStackTrace();
			return "{\"players\":[]}";
		}
	}

	public CompletableFuture<Boolean> canSeeEveryone(@NotNull String uuid) {
		Boolean cached = permissionCache.get(uuid);
		Long expiry = permissionCacheExpiry.get(uuid);
		if (expiry != null && expiry < System.currentTimeMillis()) {
			permissionCache.remove(uuid);
			permissionCacheExpiry.remove(uuid);
			cached = null;
		}
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		} else {
			CompletableFuture<User> userFuture = this.userManager.loadUser(UUID.fromString(uuid));
			return userFuture.thenApplyAsync(user -> {
				boolean value = user.getCachedData().getPermissionData().checkPermission("privatelocation.seeall").asBoolean();
				permissionCache.put(uuid, value);
				permissionCacheExpiry.put(uuid, System.currentTimeMillis() + CACHE_SECONDS * 1000);
				return value;
			});
		}
	}

	@Override
	public void onDisable() {
		getLogger().info("BlueMap-PrivateLocation disabled");
	}
}

