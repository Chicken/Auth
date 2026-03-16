package codes.antti.auth.bluemap.integration.fabric;

import codes.antti.auth.bluemap.integration.IntegrationCore;
import codes.antti.auth.bluemap.integration.IntegrationPlatformAdapter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public class FabricMod implements ModInitializer, IntegrationPlatformAdapter {
    private static final Logger LOGGER = Logger.getLogger("BlueMapAuth");
    private final IntegrationCore core = new IntegrationCore();
    private FabricConfig config;

    @Override
    public void onInitialize() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("bluemap-auth/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/bluemap-auth/config.yml");
        } catch (IOException e) {
            LOGGER.severe("Failed to load config: " + e.getMessage());
            return;
        }

        BlueMapAPI.onEnable(api -> core.onBlueMapEnabled(this, api));
        BlueMapAPI.onDisable(api -> core.onBlueMapDisabled());

        LOGGER.info("BlueMap Auth Integration enabled");
    }

    // IntegrationPlatformAdapter
    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8400); }
    @Override public String getAuthPath() { return config.getString("auth-path", "./authentication-outpost/"); }
    @Override public Logger getLogger() { return LOGGER; }
}
