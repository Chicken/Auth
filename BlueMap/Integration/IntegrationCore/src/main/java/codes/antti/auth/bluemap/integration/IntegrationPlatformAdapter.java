package codes.antti.auth.bluemap.integration;

import java.util.logging.Logger;

public interface IntegrationPlatformAdapter {
    String getIp();
    int getPort();
    String getAuthPath();
    Logger getLogger();
}
