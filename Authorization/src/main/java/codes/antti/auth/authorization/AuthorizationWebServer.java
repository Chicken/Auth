package codes.antti.auth.authorization;

import codes.antti.auth.common.http.WebServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AuthorizationWebServer {
    private final WebServer http;
    private final UserManager userManager;
    private final Path unauthorizedPagePath;
    private final ConcurrentHashMap<String, Boolean> permissionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> permissionCacheExpiry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlight = new ConcurrentHashMap<>();
    private static final long CACHE_SECONDS = 60;

    public AuthorizationWebServer(AuthorizationPlugin plugin) throws IOException {
        FileConfiguration config = plugin.getConfig();
        this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "127.0.0.1")), config.getInt("port", 8300));
        LuckPerms lp = LuckPermsProvider.get();
        this.userManager = lp.getUserManager();
        this.unauthorizedPagePath = plugin.getDataFolder().toPath().resolve("web/unauthorized.html");

        this.http.get("/auth", request -> {
            String host = request.getHeader("host");
            String playerUuid = request.getHeader("x-minecraft-uuid");
            String permissionNode = config.getString("sites." + host);
            if (playerUuid == null || permissionNode == null) {
                request.respond(403);
                return;
            }

            getPerm(playerUuid, permissionNode).thenAcceptAsync(value -> {
                try {
                    if (value) request.respond(200);
                    else request.respond(403);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        });


        this.http.get("/unauthorized", request -> {
            String host = request.getHeader("host");
            String playerUuid = request.getHeader("x-minecraft-uuid");
            String permissionNode = config.getString("sites." + host);
            if (playerUuid != null && permissionNode != null) {
                if (getPerm(playerUuid, permissionNode).get()) {
                    request.redirect(safeRedirectUri(request.getQuery().get("redirect")));
                    return;
                }
            }
            String playerName = playerUuid != null ? plugin.getServer().getOfflinePlayer(UUID.fromString(playerUuid)).getName() : "null";

            String rawUnauthorizedPage;
            try {
                rawUnauthorizedPage = Files.readString(this.unauthorizedPagePath);
            } catch (IOException e) {
                rawUnauthorizedPage = "You are not authorized to use this application.";
                plugin.getLogger().severe("Couldn't load unauthorized page, using a plain one");
                e.printStackTrace();
            }
            String unauthorizedPage =
                rawUnauthorizedPage
                    .replaceAll("\\{\\{host}}", host)
                    .replaceAll("\\{\\{permission}}", String.valueOf(permissionNode))
                    .replaceAll("\\{\\{uuid}}", String.valueOf(playerUuid))
                    .replaceAll("\\{\\{name}}", String.valueOf(playerName));

            request.setBody(unauthorizedPage, "text/html");
            request.respond(200);
        });

        this.http.serveStatic("/unauthorized/", plugin.getDataFolder().toPath().resolve("web"));


        this.http.start();
        plugin.getLogger().info("Webserver bound to " + this.http.getAddress());
    }

    private static String safeRedirectUri(@Nullable String uri) {
        if (uri == null || !uri.startsWith("/") || uri.startsWith("//")) return "/";
        return uri;
    }

    private CompletableFuture<Boolean> getPerm(@NotNull String uuid, @NotNull String permissionNode) {
        String cacheKey = uuid + ":" + permissionNode;
        Boolean cached = permissionCache.get(cacheKey);
        Long expiry = permissionCacheExpiry.get(cacheKey);
        if (cached != null && expiry != null && expiry >= System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Boolean> myFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = inFlight.putIfAbsent(cacheKey, myFuture);
        if (existing != null) {
            return existing;
        }
        UUID parsedUuid = UUID.fromString(uuid);
        User cachedUser = this.userManager.getUser(parsedUuid);
        (cachedUser != null ? CompletableFuture.completedFuture(cachedUser) : this.userManager.loadUser(parsedUuid)).thenAcceptAsync(user -> {
            boolean value = user.getCachedData().getPermissionData().checkPermission(permissionNode).asBoolean();
            permissionCache.put(cacheKey, value);
            permissionCacheExpiry.put(cacheKey, System.currentTimeMillis() + CACHE_SECONDS * 1000);
            inFlight.remove(cacheKey);
            myFuture.complete(value);
        }).exceptionally(ex -> {
            inFlight.remove(cacheKey);
            myFuture.completeExceptionally(ex);
            return null;
        });
        return myFuture;
    }

    public void close() {
        this.http.close();
    }
}
