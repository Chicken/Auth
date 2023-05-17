package codes.antti.auth.authorization;

import codes.antti.auth.common.WebServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AuthorizationWebServer {
    private final WebServer http;
    private String unauthorizedPage = null;
    private final HashMap<String, Boolean> permissionCache = new HashMap<>();
    private final HashMap<String, Long> permissionCacheExpiry = new HashMap<>();
    private static final long CACHE_SECONDS = 15;
    public AuthorizationWebServer(AuthorizationPlugin plugin) throws IOException {
        FileConfiguration config = plugin.getConfig();
        this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8300));
        LuckPerms lp = LuckPermsProvider.get();
        UserManager userManager = lp.getUserManager();

        this.http.handle("/auth", request -> {
            String host = request.httpExchange.getRequestHeaders().get("host").get(0);
            String playerUuid = request.httpExchange.getRequestHeaders().get("x-minecraft-uuid").get(0);
            String permissionNode = config.getString("sites." + host);
            if (playerUuid == null || permissionNode == null) {
                request.respond(403);
                return;
            }
            Boolean cached = permissionCache.get(playerUuid);
            Long expiry = permissionCacheExpiry.get(playerUuid);
            if (expiry != null && expiry < System.currentTimeMillis()) {
                permissionCache.remove(playerUuid);
                permissionCacheExpiry.remove(playerUuid);
                cached = null;
            }
            if (cached != null) {
                if (cached) request.respond(200);
                else request.respond(403);
            } else {
                CompletableFuture<User> userFuture = userManager.loadUser(UUID.fromString(playerUuid));
                userFuture.thenAcceptAsync(user -> {
                    try {
                        List<Node> nodes = user.resolveInheritedNodes(QueryOptions.nonContextual()).stream().filter(n -> n.getKey().equals(permissionNode)).collect(Collectors.toList());
                        // plugin.getLogger().info("Count: " + nodes.size());
                        Optional<Node> node = nodes.stream().findFirst();
                        if (node.isPresent() && node.get().getValue()) {
                            permissionCache.put(playerUuid, true);
                            permissionCacheExpiry.put(playerUuid, System.currentTimeMillis() +  CACHE_SECONDS * 1000);
                            // plugin.getLogger().info("UUID: " + playerUuid + " HOST: " + host + " PERM: " + permissionNode + " = true");
                            request.respond(200);
                            return;
                        }
                        permissionCache.put(playerUuid, false);
                        permissionCacheExpiry.put(playerUuid, System.currentTimeMillis() +  CACHE_SECONDS * 1000);
                        // plugin.getLogger().info("UUID: " + playerUuid + " HOST: " + host + " PERM: " + permissionNode + " = false");
                        request.respond(403);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
            }
        });



        this.http.handle("/unauthorized", request -> {
            String host = request.httpExchange.getRequestHeaders().get("host").get(0);
            String playerUuid = request.httpExchange.getRequestHeaders().get("x-minecraft-uuid").get(0);
            String permissionNode = config.getString("sites." + host);
            if (playerUuid != null && permissionNode != null) {
                CompletableFuture<User> userFuture = userManager.loadUser(UUID.fromString(playerUuid));
                Optional<Node> node = userFuture.get().resolveInheritedNodes(QueryOptions.nonContextual()).stream().filter(n -> n.getKey().equals(permissionNode)).findFirst();
                if (node.isPresent() && node.get().getValue()) {
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

    public void close() {
        this.http.close();
    }
}
