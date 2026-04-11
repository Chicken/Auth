package codes.antti.auth.bluemap.privatemaps.fabric;

import codes.antti.auth.bluemap.privatemaps.PrivateMapsCore;
import codes.antti.auth.bluemap.privatemaps.PrivateMapsPlatformAdapter;
import codes.antti.auth.common.fabric.FabricConfig;
import codes.antti.auth.common.fabric.LogWrapper;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, PrivateMapsPlatformAdapter {
    private static final Logger LOGGER = new LogWrapper(LoggerFactory.getLogger("BlueMap-PrivateMaps"), "[BlueMap-PrivateMaps]");
    private final PrivateMapsCore core = new PrivateMapsCore();
    private FabricConfig config;

    @Override
    public void onInitializeServer() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("bluemap-privatemaps/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/bluemap-privatemaps/config.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            return;
        }

        core.onEnable(this);
    }

    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8600); }
    @Override public String getBlueMapUrl() { return config.getString("bluemap-url", "http://localhost:8100"); }
    @Override public Logger getLogger() { return LOGGER; }
    @Override public LuckPerms getLuckPerms() { return LuckPermsProvider.get(); }
}
