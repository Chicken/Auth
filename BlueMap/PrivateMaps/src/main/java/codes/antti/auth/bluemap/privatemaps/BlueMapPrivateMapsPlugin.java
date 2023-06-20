package codes.antti.auth.bluemap.privatemaps;

import codes.antti.auth.common.http.WebServer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class BlueMapPrivateMapsPlugin extends JavaPlugin {
	private WebServer http;
	private static final Gson gson = new Gson();
	private LuckPerms lp;
	private final HashMap<String, Boolean> permissionCache = new HashMap<>();
	private final HashMap<String, Long> permissionCacheExpiry = new HashMap<>();
	private static final long CACHE_SECONDS = 60;
    @Override
    public void onEnable() {
        saveDefaultConfig();
		Configuration config = getConfig();
		this.lp = LuckPermsProvider.get();

        try {
            this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8600));
        } catch (IOException ex) {
            getLogger().severe("Failed to create HTTP server");
            ex.printStackTrace();
            return;
        }

		URL settingsUrl;
		try {
			settingsUrl = new URL(Objects.requireNonNull(config.getString("bluemap-url")) + "/settings.json");
		} catch (MalformedURLException ex) {
			getLogger().severe("Failed to parse bluemap-url");
			ex.printStackTrace();
			return;
		}
		this.http.get("/settings.json", (request) -> {
			String playerUuid = request.getHeader("x-minecraft-uuid");
			String loggedIn = request.getHeader("x-minecraft-loggedin");
			if (loggedIn == null || (loggedIn.equals("true") && playerUuid == null)) {
				request.respond(400);
				return;
			}
			HttpURLConnection conn = (HttpURLConnection) settingsUrl.openConnection();
			conn.setRequestMethod("GET");
			InputStreamReader in = new InputStreamReader(conn.getInputStream());
			JsonObject json = gson.fromJson(in, JsonObject.class);
			in.close();
			conn.disconnect();
			List<String> maps = json.getAsJsonArray("maps").asList().stream().map(JsonElement::getAsString).collect(Collectors.toList());
			JsonArray newMaps = new JsonArray();
			List<CompletableFuture<Boolean>> futures = maps.stream().map((map) -> permCheck("auth.maps." + map, playerUuid)).collect(Collectors.toList());
			CompletableFuture
					.allOf(futures.toArray(CompletableFuture[]::new))
					.thenAcceptAsync((cf) -> {
						List<Boolean> checks = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
						for (int i = 0; i < maps.size(); i++) {
							if (checks.get(i)) {
								newMaps.add(maps.get(i));
							}
						}
						json.remove("maps");
						json.add("maps", newMaps);
						request.json(json);
						try {
							request.respond(200);
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					});
		});

		this.http.get("/auth", (request) -> {
			String playerUuid = request.getHeader("x-minecraft-uuid");
			String originalUri = request.getHeader("x-original-uri");
			String loggedIn = request.getHeader("x-minecraft-loggedin");
			if (loggedIn == null || originalUri == null || (loggedIn.equals("true") && playerUuid == null)) {
				request.respond(400);
				return;
			}
			String map = originalUri.split("/")[2];
			permCheck("auth.maps." + map, playerUuid).thenAcceptAsync((hasPerm) -> {
				try {
					if (hasPerm) request.respond(200);
					else request.respond(403);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			});
		});

        this.http.start();
        getLogger().info("Webserver bound to " + this.http.getAddress());

        getLogger().info("BlueMap-PrivateMaps enabled");
    }

	public CompletableFuture<Boolean> permCheck(@NotNull String node, @Nullable String uuid) {
		if (uuid == null) uuid = "default";
		String cacheKey = node + ":" + uuid;
		Boolean cached = permissionCache.get(cacheKey);
		Long expiry = permissionCacheExpiry.get(cacheKey);
		if (expiry != null && expiry < System.currentTimeMillis()) {
			permissionCache.remove(cacheKey);
			permissionCacheExpiry.remove(cacheKey);
			cached = null;
		}
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		} else {
			CompletableFuture<? extends PermissionHolder> permFuture = uuid.equals("default")
					? this.lp.getGroupManager().loadGroup("default").thenApply(go -> go.orElse(null))
					: this.lp.getUserManager().loadUser(UUID.fromString(uuid));
			return permFuture.thenApplyAsync(holder -> {
				if (holder == null) return false;
				boolean value = holder.getCachedData().getPermissionData().checkPermission(node).asBoolean();
				permissionCache.put(cacheKey, value);
				permissionCacheExpiry.put(cacheKey, System.currentTimeMillis() + CACHE_SECONDS * 1000);
				return value;
			});
		}
	}

    @Override
    public void onDisable() {
        if (this.http != null) this.http.close();
        getLogger().info("BlueMap-PrivateMaps disabled");
    }
}

