package codes.antti.auth.bluemap.integration.spigot;

import codes.antti.auth.bluemap.integration.IntegrationCore;
import codes.antti.auth.bluemap.integration.IntegrationPlatformAdapter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlueMapAuthIntegrationPlugin extends JavaPlugin implements IntegrationPlatformAdapter {
    private final IntegrationCore core = new IntegrationCore();

    @Override
    public void onEnable() {
        BlueMapAPI.onEnable(api -> {
            reloadConfig();
            saveDefaultConfig();
            core.onBlueMapEnabled(this, api);
        });
        BlueMapAPI.onDisable(api -> core.onBlueMapDisabled());
        getLogger().info("BlueMap Auth Integration enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("BlueMap Auth Integration disabled");
    }

    // IntegrationPlatformAdapter
    @Override public String getIp() { return getConfig().getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return getConfig().getInt("port", 8400); }
    @Override public String getAuthPath() { return getConfig().getString("auth-path", "./authentication-outpost/"); }
}
