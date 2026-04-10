package codes.antti.auth.bluemap.chat;

import java.util.logging.Logger;

public interface ChatPlatformAdapter {
    String getIp();
    int getPort();
    boolean isReadAuth();
    boolean isReadOnly();
    int getMaxMessageCount();
    String getWebChatPrefix();
    String getAuthPath();
    boolean isRegisterWebApp();
    Logger getLogger();
    void broadcastToServer(String formattedMessage);
}
