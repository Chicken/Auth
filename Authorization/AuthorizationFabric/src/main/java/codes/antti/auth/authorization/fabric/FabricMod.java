package codes.antti.auth.authorization.fabric;

import codes.antti.auth.authorization.AuthorizationCore;
import codes.antti.auth.authorization.AuthorizationPlatformAdapter;
import codes.antti.auth.common.fabric.FabricConfig;
import codes.antti.auth.common.fabric.LogWrapper;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, AuthorizationPlatformAdapter {
    private static final Logger LOGGER = new LogWrapper(LoggerFactory.getLogger("Authorization"), "[Authorization]");
    private final AuthorizationCore core = new AuthorizationCore();
    private FabricConfig config;
    private MinecraftServer server;

    @Override
    public void onInitializeServer() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("authorization/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/authorization/config.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            return;
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            core.onEnable(this);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> core.onDisable());

    }

    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8300); }
    @Override public Map<String, String> getSites() { return config.getStringMap("sites"); }
    @Override public Path getDataPath() { return FabricLoader.getInstance().getConfigDir().resolve("authorization"); }

    @Override
    public @Nullable String getPlayerName(UUID uuid) {
        if (server == null) return null;
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return null;
    }

    @Override public Logger getLogger() { return LOGGER; }
    @Override public LuckPerms getLuckPerms() { return LuckPermsProvider.get(); }
}
