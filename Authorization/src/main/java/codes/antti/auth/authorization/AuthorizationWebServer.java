package codes.antti.auth.authorization;

import codes.antti.auth.common.WebServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AuthorizationWebServer {
    private final WebServer http;
    private final UserManager userManager;
    private String unauthorizedPage = null;
    private final HashMap<String, Boolean> permissionCache = new HashMap<>();
    private final HashMap<String, Long> permissionCacheExpiry = new HashMap<>();
    private static final long CACHE_SECONDS = 15;

    public AuthorizationWebServer(AuthorizationPlugin plugin) throws IOException {
        FileConfiguration config = plugin.getConfig();
        this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8300));
        LuckPerms lp = LuckPermsProvider.get();
        this.userManager = lp.getUserManager();

        this.http.handle("/auth", request -> {
            String host = request.httpExchange.getRequestHeaders().get("host").get(0);
            String playerUuid = request.httpExchange.getRequestHeaders().get("x-minecraft-uuid").get(0);
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



        this.http.handle("/unauthorized", request -> {
            String host = request.httpExchange.getRequestHeaders().get("host").get(0);
            String playerUuid = request.httpExchange.getRequestHeaders().get("x-minecraft-uuid").get(0);
            String permissionNode = config.getString("sites." + host);
            if (playerUuid != null && permissionNode != null) {
                if (getPerm(playerUuid, permissionNode).get()) {
                    request.redirect("/");
                    return;
                }
            }

            if (this.unauthorizedPage == null) {
                try {
                    this.unauthorizedPage = Files.readString(plugin.getDataFolder().toPath().resolve("unauthorized.html"));
                } catch (IOException e) {
                    this.unauthorizedPage = "You are not authorized to use this application.";
                    plugin.getLogger().severe("Couldn't load unauthorized page, using a plain one");
                    e.printStackTrace();
                }
            }

            request.setBody(this.unauthorizedPage, "text/html");
            request.respond(200);
        });



        this.http.start();
        plugin.getLogger().info("Webserver bound to " + this.http.getAddress());
    }

    private CompletableFuture<Boolean> getPerm(@NotNull String uuid, @NotNull String permissionNode) {
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
                boolean value = user.getCachedData().getPermissionData().checkPermission(permissionNode).asBoolean();
                permissionCache.put(uuid, value);
                permissionCacheExpiry.put(uuid, System.currentTimeMillis() + CACHE_SECONDS * 1000);
                return value;
            });
        }
    }

    public void close() {
        this.http.close();
    }
}
