package codes.antti.auth.authentication;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface AuthPlatformAdapter {
    Path getDataPath();
    String getIp();
    int getPort();
    boolean isWebServerEnabled();
    boolean isIpLock();
    boolean isOptionalAuthentication();
    int getSessionLengthDays();
    int getAuthTokenLength();
    int getUserMaxSessions();
    String getDatabaseUrl();
    @Nullable String getDriverJar();
    @Nullable String getDriverClass();
    Logger getLogger();
}
