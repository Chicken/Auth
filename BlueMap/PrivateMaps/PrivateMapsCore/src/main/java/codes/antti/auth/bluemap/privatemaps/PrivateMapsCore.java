package codes.antti.auth.bluemap.privatemaps;

import codes.antti.auth.common.http.WebServer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PrivateMapsCore {
    private WebServer http;
    private PrivateMapsPlatformAdapter adapter;
    private static final Gson gson = new Gson();
    private final ConcurrentHashMap<String, CompletableFuture<PermissionHolder>> userCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> userCacheExpiry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> permCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> permCacheExpiry = new ConcurrentHashMap<>();
    private static final long CACHE_SECONDS = 60;

    public void onEnable(PrivateMapsPlatformAdapter adapter) {
        this.adapter = adapter;

        try {
            this.http = new WebServer(adapter.getIp(), adapter.getPort());
        } catch (IOException ex) {
            adapter.getLogger().severe("Failed to create HTTP server");
            ex.printStackTrace();
            return;
        }

        URL settingsUrl;
        try {
            settingsUrl = new URI(Objects.requireNonNull(adapter.getBlueMapUrl()) + "/settings.json").toURL();
        } catch (URISyntaxException | MalformedURLException ex) {
            adapter.getLogger().severe("Failed to parse bluemap-url");
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
        adapter.getLogger().info("Webserver bound to " + this.http.getAddress());
        adapter.getLogger().info("BlueMap-PrivateMaps enabled");
    }

    private CompletableFuture<PermissionHolder> loadCachedUser(String uuid) {
        long now = System.currentTimeMillis();
        return userCache.compute(uuid, (key, existing) -> {
            Long expiry = userCacheExpiry.get(key);
            if (existing != null && expiry != null && expiry >= now) {
                return existing;
            }
            CompletableFuture<PermissionHolder> future;
            if (key.equals("default")) {
                future = CompletableFuture.completedFuture(adapter.getLuckPerms().getGroupManager().getGroup("default"));
            } else {
                UUID parsedKey = UUID.fromString(key);
                User cachedUser = adapter.getLuckPerms().getUserManager().getUser(parsedKey);
                future = cachedUser != null
                    ? CompletableFuture.completedFuture(cachedUser)
                    : adapter.getLuckPerms().getUserManager().loadUser(parsedKey).thenApply(u -> u);
            }
            userCacheExpiry.put(key, now + CACHE_SECONDS * 1000);
            future.exceptionally(ex -> {
                userCache.remove(key, future);
                userCacheExpiry.remove(key);
                return null;
            });
            return future;
        });
    }

    public CompletableFuture<Boolean> permCheck(@NotNull String node, @Nullable String uuid) {
        if (uuid == null) uuid = "default";
        String cacheKey = uuid + ":" + node;
        long now = System.currentTimeMillis();
        Long expiry = permCacheExpiry.get(cacheKey);
        if (expiry != null && expiry >= now) {
            return CompletableFuture.completedFuture(permCache.get(cacheKey));
        }
        return loadCachedUser(uuid).thenApply(holder -> {
            boolean result = holder != null && holder.getCachedData().getPermissionData().checkPermission(node).asBoolean();
            permCache.put(cacheKey, result);
            permCacheExpiry.put(cacheKey, now + (CACHE_SECONDS / 4) * 1000);
            return result;
        });
    }

    public void onDisable() {
        if (this.http != null) this.http.close();
    }
}
