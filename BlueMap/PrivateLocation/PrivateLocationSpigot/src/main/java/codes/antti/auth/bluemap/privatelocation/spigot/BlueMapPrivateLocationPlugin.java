package codes.antti.auth.bluemap.privatelocation.spigot;

import codes.antti.auth.bluemap.privatelocation.PlayerInfo;
import codes.antti.auth.bluemap.privatelocation.PrivateLocationCore;
import codes.antti.auth.bluemap.privatelocation.PrivateLocationPlatformAdapter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class BlueMapPrivateLocationPlugin extends JavaPlugin implements PrivateLocationPlatformAdapter {
    private final PrivateLocationCore core = new PrivateLocationCore();

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();

        BlueMapAPI.onEnable(api -> core.onBlueMapEnabled(this, api));
        BlueMapAPI.onDisable(api -> core.onBlueMapDisabled());

        getLogger().info("BlueMap-PrivateLocation enabled");
    }

    @Override
    public void onDisable() {
        core.onBlueMapDisabled();
        getLogger().info("BlueMap-PrivateLocation disabled");
    }

    @Override public String getIp() { return getConfig().getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return getConfig().getInt("port", 8500); }
    @Override public Logger getLogger() { return super.getLogger(); }
    @Override public UserManager getLuckPermsUserManager() { return LuckPermsProvider.get().getUserManager(); }

    @Override
    public Optional<List<PlayerInfo>> getVisiblePlayers(BlueMapAPI api, BlueMapWorld targetWorld, UUID viewerUuid, boolean showAll) {
        Optional<World> worldOpt = getServer().getWorlds().stream()
            .filter(w -> api.getWorld(w).map(bmw -> bmw.equals(targetWorld)).orElse(false))
            .findFirst();
        if (worldOpt.isEmpty()) return Optional.empty();
        World world = worldOpt.get();

        List<Player> candidates;
        if (showAll) {
            candidates = new ArrayList<>(getServer().getOnlinePlayers());
        } else {
            Player viewer = getServer().getPlayer(viewerUuid);
            candidates = viewer != null ? List.of(viewer) : List.of();
        }

        List<PlayerInfo> result = new ArrayList<>();
        for (Player player : candidates) {
            if (player != null && player.isOnline()) {
                result.add(new PlayerInfo(
                    player.getUniqueId(),
                    player.getName(),
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getLocation().getDirection().getX(),
                    player.getLocation().getDirection().getY(),
                    player.getLocation().getDirection().getZ(),
                    !player.getWorld().equals(world)
                ));
            }
        }
        return Optional.of(result);
    }
}
