package codes.antti.auth.bluemap.privatemaps.spigot;

import codes.antti.auth.bluemap.privatemaps.PrivateMapsCore;
import codes.antti.auth.bluemap.privatemaps.PrivateMapsPlatformAdapter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class BlueMapPrivateMapsPlugin extends JavaPlugin implements PrivateMapsPlatformAdapter {
    private final PrivateMapsCore core = new PrivateMapsCore();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core.onEnable(this);
    }

    @Override
    public void onDisable() {
        core.onDisable();
        getLogger().info("BlueMap-PrivateMaps disabled");
    }

    @Override public String getIp() { return getConfig().getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return getConfig().getInt("port", 8600); }
    @Override public String getBlueMapUrl() { return getConfig().getString("bluemap-url", "http://localhost:8100"); }
    @Override public Logger getLogger() { return super.getLogger(); }
    @Override public LuckPerms getLuckPerms() { return LuckPermsProvider.get(); }
}
