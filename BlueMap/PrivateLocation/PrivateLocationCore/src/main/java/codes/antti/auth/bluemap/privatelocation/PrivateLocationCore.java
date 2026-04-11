package codes.antti.auth.bluemap.privatelocation;

import codes.antti.auth.common.http.WebServer;
import com.google.gson.stream.JsonWriter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PrivateLocationCore {
    private WebServer http;
    private final ConcurrentHashMap<String, Boolean> permissionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> permissionCacheExpiry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlight = new ConcurrentHashMap<>();
    private static final long CACHE_SECONDS = 60;

    public void onBlueMapEnabled(PrivateLocationPlatformAdapter adapter, BlueMapAPI api) {
        try {
            this.http = new WebServer(adapter.getIp(), adapter.getPort());
        } catch (IOException ex) {
            adapter.getLogger().severe("Failed to create HTTP server");
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
            UUID parsedUuid = UUID.fromString(playerUuid);
            canSeeEveryone(playerUuid, adapter).thenAcceptAsync((showAll) -> {
                Optional<List<PlayerInfo>> playersOpt = adapter.getVisiblePlayers(api, targetWorld, parsedUuid, showAll);
                if (playersOpt.isEmpty()) {
                    try {
                        request.respond(404);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return;
                }
                String response = buildJson(playersOpt.get(), api, map.get(), adapter);
                request.setBody(response, "application/json");
                try {
                    request.respond(200);
                } catch (IOException ex) {
                    adapter.getLogger().severe("Failed to send response");
                    ex.printStackTrace();
                }
            });
        });

        this.http.start();
        adapter.getLogger().info("Webserver bound to " + this.http.getAddress());
    }

    public void onBlueMapDisabled() {
        if (this.http != null) this.http.close();
    }

    private String buildJson(List<PlayerInfo> players, BlueMapAPI api, BlueMapMap map, PrivateLocationPlatformAdapter adapter) {
        try (StringWriter jsonString = new StringWriter();
             JsonWriter json = new JsonWriter(jsonString)) {

            json.beginObject();
            json.name("players").beginArray();

            for (PlayerInfo player : players) {
                try {
                    BMSkin.createPlayerHeadIfNotExists(api, map, player.uuid());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                json.beginObject();
                json.name("uuid").value(player.uuid().toString());
                json.name("name").value(player.name());
                json.name("foreign").value(player.foreign());

                json.name("position").beginObject();
                json.name("x").value(player.x());
                json.name("y").value(player.y());
                json.name("z").value(player.z());
                json.endObject();

                json.name("rotation").beginObject();
                json.name("pitch").value(player.dirX());
                json.name("yaw").value(player.dirY());
                json.name("roll").value(player.dirZ());
                json.endObject();

                json.endObject();
            }

            json.endArray();
            json.endObject();

            json.flush();
            return jsonString.toString();
        } catch (IOException ex) {
            adapter.getLogger().severe("Failed to create JSON response");
            ex.printStackTrace();
            return "{\"players\":[]}";
        }
    }

    public CompletableFuture<Boolean> canSeeEveryone(@NotNull String uuid, PrivateLocationPlatformAdapter adapter) {
        Boolean cached = permissionCache.get(uuid);
        Long expiry = permissionCacheExpiry.get(uuid);
        if (cached != null && expiry != null && expiry >= System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Boolean> myFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = inFlight.putIfAbsent(uuid, myFuture);
        if (existing != null) {
            return existing;
        }
        UUID parsedUuid = UUID.fromString(uuid);
        UserManager userManager = adapter.getLuckPermsUserManager();
        User cachedUser = userManager.getUser(parsedUuid);
        (cachedUser != null ? CompletableFuture.completedFuture(cachedUser) : userManager.loadUser(parsedUuid)).thenAcceptAsync(user -> {
            boolean value = user.getCachedData().getPermissionData().checkPermission("privatelocation.seeall").asBoolean();
            permissionCache.put(uuid, value);
            permissionCacheExpiry.put(uuid, System.currentTimeMillis() + CACHE_SECONDS * 1000);
            inFlight.remove(uuid);
            myFuture.complete(value);
        }).exceptionally(ex -> {
            inFlight.remove(uuid);
            myFuture.completeExceptionally(ex);
            return null;
        });
        return myFuture;
    }
}
