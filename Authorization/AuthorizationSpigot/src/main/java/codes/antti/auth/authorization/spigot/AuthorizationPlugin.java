package codes.antti.auth.authorization.spigot;

import codes.antti.auth.authorization.AuthorizationCore;
import codes.antti.auth.authorization.AuthorizationPlatformAdapter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class AuthorizationPlugin extends JavaPlugin implements AuthorizationPlatformAdapter {
    private final AuthorizationCore core = new AuthorizationCore();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core.onEnable(this);
    }

    @Override
    public void onDisable() {
        core.onDisable();
        getLogger().info("Going down");
    }

    @Override public String getIp() { return getConfig().getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return getConfig().getInt("port", 8300); }

    @Override
    public Map<String, String> getSites() {
        ConfigurationSection section = getConfig().getConfigurationSection("sites");
        if (section == null) return Collections.emptyMap();
        Map<String, String> sites = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) sites.put(key, value);
        }
        return sites;
    }

    @Override public Path getDataPath() { return getDataFolder().toPath(); }

    @Override
    public @Nullable String getPlayerName(UUID uuid) {
        return getServer().getOfflinePlayer(uuid).getName();
    }

    @Override public Logger getLogger() { return super.getLogger(); }
    @Override public LuckPerms getLuckPerms() { return LuckPermsProvider.get(); }
}
