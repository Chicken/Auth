package codes.antti.auth.bluemap.chat.spigot;

import codes.antti.auth.bluemap.chat.ChatCore;
import codes.antti.auth.bluemap.chat.ChatPlatformAdapter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlueMapChatPlugin extends JavaPlugin implements Listener, ChatPlatformAdapter {
    private final ChatCore core = new ChatCore();

    @Override
    public void onLoad() {
        BlueMapAPI.onEnable(api -> {
            reloadConfig();
            saveDefaultConfig();
            core.onBlueMapEnabled(this, api);
        });
    }

    @Override
    public void onEnable() {
        core.onStart();
        getServer().getPluginManager().registerEvents(this, this);

        BlueMapAPI.onDisable(api -> core.onBlueMapDisabled());
        getLogger().info("BlueMap Chat enabled");
    }

    @Override
    public void onDisable() {
        core.onStop();
        getLogger().info("BlueMap Chat disabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMessage(AsyncPlayerChatEvent event) {
        core.onChat(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (event.getCommand().startsWith("say ")) {
            core.onSayCommand(event.getCommand().substring(4).trim());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandSend(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().startsWith("/say ")) {
            core.onPlayerSayCommand(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage().substring(5).trim());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;
        core.onDeath(event.getEntity().getUniqueId(), event.getEntity().getName(), event.getDeathMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        core.onJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        core.onQuit(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    // ChatPlatformAdapter
    @Override public String getIp() { return getConfig().getString("ip", "0.0.0.0"); }
    @Override public int getPort() { return getConfig().getInt("port", 8800); }
    @Override public boolean isReadAuth() { return getConfig().getBoolean("read-auth", false); }
    @Override public boolean isReadOnly() { return getConfig().getBoolean("read-only", false); }
    @Override public int getMaxMessageCount() { return getConfig().getInt("max-message-count", 100); }
    @Override public String getWebChatPrefix() { return getConfig().getString("web-chat-prefix", "[web]"); }
    @Override public String getAuthPath() { return getConfig().getString("auth-path", "/authentication-outpost/"); }
    @Override public void broadcastToServer(String formattedMessage) { getServer().broadcastMessage(formattedMessage); }
}
