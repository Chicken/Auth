package codes.antti.auth.authentication;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface AuthPlatformAdapter {
    Path getDataPath();
    String getIp();
    int getPort();
    boolean isIpLock();
    boolean isOptionalAuthentication();
    int getSessionLengthDays();
    int getAuthTokenLength();
    int getUserMaxSessions();
    Logger getLogger();
}
