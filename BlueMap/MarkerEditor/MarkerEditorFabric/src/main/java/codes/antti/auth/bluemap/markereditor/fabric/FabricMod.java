package codes.antti.auth.bluemap.markereditor.fabric;

import codes.antti.auth.bluemap.markereditor.MarkerEditorCore;
import codes.antti.auth.bluemap.markereditor.MarkerEditorPlatformAdapter;
import codes.antti.auth.common.fabric.FabricConfig;
import codes.antti.auth.common.fabric.LogWrapper;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, MarkerEditorPlatformAdapter {
    private static final Logger LOGGER = new LogWrapper(LoggerFactory.getLogger("BlueMap-MarkerEditor"), "[BlueMap-MarkerEditor]");

    private final MarkerEditorCore core = new MarkerEditorCore();
    private final Consumer<BlueMapAPI> blueMapEnableListener = core::onBlueMapEnabled;
    private final Consumer<BlueMapAPI> blueMapDisableListener = api -> core.onBlueMapDisabled();
    private FabricConfig config;

    @Override
    public void onInitializeServer() {
        Path configFile = getDataPath().resolve("config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/bluemap-marker-editor/config.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            return;
        }

        core.onStart(this);
        BlueMapAPI.onEnable(blueMapEnableListener);
        BlueMapAPI.onDisable(blueMapDisableListener);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BlueMapAPI.unregisterListener(blueMapEnableListener);
            BlueMapAPI.unregisterListener(blueMapDisableListener);
            core.onBlueMapDisabled();
            core.onStop();
        });

        getLogger().info("BlueMap MarkerEditor enabled");
    }

    @Override public Path getDataPath() { return FabricLoader.getInstance().getConfigDir().resolve("bluemap-marker-editor"); }
    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8700); }
    @Override public String getDatabaseUrl() { return config.getString("database_url", "jdbc:sqlite:{data}/markers.db"); }
    @Override public String getDriverJar() { return config.getString("driver_jar", null); }
    @Override public String getDriverClass() { return config.getString("driver_class", null); }
    @Override public String getAuthPath() { return config.getString("auth-path", "/authentication-outpost/"); }
    @Override public boolean isRegisterWebApp() { return config.getBoolean("register-web-app", true); }
    @Override public Logger getLogger() { return LOGGER; }
    @Override public LuckPerms getLuckPerms() { return LuckPermsProvider.get(); }
}
