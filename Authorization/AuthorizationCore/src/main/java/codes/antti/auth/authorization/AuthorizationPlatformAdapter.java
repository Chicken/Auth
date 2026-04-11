package codes.antti.auth.authorization;

import net.luckperms.api.LuckPerms;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public interface AuthorizationPlatformAdapter {
    String getIp();
    int getPort();
    Map<String, String> getSites();
    Path getDataPath();
    @Nullable String getPlayerName(UUID uuid);
    Logger getLogger();
    LuckPerms getLuckPerms();
}
