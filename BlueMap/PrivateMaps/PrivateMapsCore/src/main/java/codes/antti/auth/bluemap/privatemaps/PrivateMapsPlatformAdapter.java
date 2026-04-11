package codes.antti.auth.bluemap.privatemaps;

import net.luckperms.api.LuckPerms;

import java.util.logging.Logger;

public interface PrivateMapsPlatformAdapter {
    String getIp();
    int getPort();
    String getBlueMapUrl();
    Logger getLogger();
    LuckPerms getLuckPerms();
}
