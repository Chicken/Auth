package codes.antti.auth.bluemap.privatelocation.fabric;

import codes.antti.auth.bluemap.privatelocation.PlayerInfo;
import codes.antti.auth.bluemap.privatelocation.PrivateLocationCore;
import codes.antti.auth.bluemap.privatelocation.PrivateLocationPlatformAdapter;
import codes.antti.auth.common.fabric.FabricConfig;
import codes.antti.auth.common.fabric.LogWrapper;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, PrivateLocationPlatformAdapter {
    private static final Logger LOGGER = new LogWrapper(LoggerFactory.getLogger("BlueMap-PrivateLocation"), "[BlueMap-PrivateLocation]");
    private final PrivateLocationCore core = new PrivateLocationCore();
    private FabricConfig config;
    private MinecraftServer server;

    @Override
    public void onInitializeServer() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("bluemap-privatelocation/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/bluemap-privatelocation/config.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            return;
        }

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> this.server = srv);

        BlueMapAPI.onEnable(api -> core.onBlueMapEnabled(this, api));
        BlueMapAPI.onDisable(api -> core.onBlueMapDisabled());

        getLogger().info("BlueMap-PrivateLocation enabled");
    }

    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8500); }
    @Override public Logger getLogger() { return LOGGER; }
    @Override public UserManager getLuckPermsUserManager() { return LuckPermsProvider.get().getUserManager(); }

    @Override
    public Optional<List<PlayerInfo>> getVisiblePlayers(BlueMapAPI api, BlueMapWorld targetWorld, UUID viewerUuid, boolean showAll) {
        if (server == null) return Optional.empty();

        ServerLevel world = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (api.getWorld(level).map(bmw -> bmw.equals(targetWorld)).orElse(false)) {
                world = level;
                break;
            }
        }
        if (world == null) return Optional.empty();

        List<ServerPlayer> candidates;
        if (showAll) {
            candidates = server.getPlayerList().getPlayers();
        } else {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerUuid);
            candidates = viewer != null ? List.of(viewer) : List.of();
        }

        List<PlayerInfo> result = new ArrayList<>();
        for (ServerPlayer player : candidates) {
            result.add(new PlayerInfo(
                player.getUUID(),
                player.getName().getString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getLookAngle().x,
                player.getLookAngle().y,
                player.getLookAngle().z,
                !player.level().equals(world)
            ));
        }
        return Optional.of(result);
    }
}
