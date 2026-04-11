package codes.antti.auth.bluemap.privatelocation;

import java.util.UUID;

public record PlayerInfo(
    UUID uuid,
    String name,
    double x,
    double y,
    double z,
    double dirX,
    double dirY,
    double dirZ,
    boolean foreign
) {}
