package codes.antti.auth.bluemap.markereditor;

import net.luckperms.api.LuckPerms;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface MarkerEditorPlatformAdapter {
    Path getDataPath();
    String getIp();
    int getPort();
    String getDatabaseUrl();
    @Nullable String getDriverJar();
    @Nullable String getDriverClass();
    String getAuthPath();
    boolean isRegisterWebApp();
    Logger getLogger();
    LuckPerms getLuckPerms();
}
