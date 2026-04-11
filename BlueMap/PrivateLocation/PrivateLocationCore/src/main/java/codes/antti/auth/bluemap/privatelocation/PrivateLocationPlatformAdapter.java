package codes.antti.auth.bluemap.privatelocation;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import net.luckperms.api.model.user.UserManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public interface PrivateLocationPlatformAdapter {
    String getIp();
    int getPort();
    Logger getLogger();
    UserManager getLuckPermsUserManager();

    /**
     * Returns the players visible to the viewer on the given world/map.
     * Returns an empty Optional if the world is not available on this platform.
     */
    Optional<List<PlayerInfo>> getVisiblePlayers(BlueMapAPI api, BlueMapWorld targetWorld, UUID viewerUuid, boolean showAll);
}
