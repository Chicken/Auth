package codes.antti.auth.bluemap.markereditor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class MarkerModels {
    private MarkerModels() {}

    enum Scope {
        GLOBAL,
        PRIVATE;

        static Scope parse(String value) throws ApiException {
            if (value == null) throw ApiException.field("scope", "Scope is required");
            return switch (value) {
                case "global" -> GLOBAL;
                case "private" -> PRIVATE;
                default -> throw ApiException.field("scope", "Scope must be global or private");
            };
        }

        String jsonName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static class SetInput {
        String key;
        String scope;
        String label;
        Boolean toggleable;
        Boolean defaultHidden;
        Integer sorting;
    }

    static final class SetModel extends SetInput {
        String id;
        List<MarkerModel> markers = new ArrayList<>();
    }

    static class MarkerData {
        String key;
        String type;
        String label;
        String positionMode;
        Vec3 position;
        Integer sorting;
        Boolean listed;
        Double minDistance;
        Double maxDistance;

        String detail;
        String icon;
        Anchor anchor;
        List<String> styleClasses;
        String html;

        @Nullable String link;
        Boolean newTab;
        List<Vec3> line;
        List<Vec2> shape;
        List<List<Vec2>> holes;
        Double shapeY;
        Double minY;
        Double maxY;
        Boolean depthTest;
        Integer lineWidth;
        ColorData lineColor;
        ColorData fillColor;
    }

    static final class MarkerModel extends MarkerData {
        String id;
    }

    static final class Vec2 {
        Double x;
        Double z;

        Vec2() {}

        Vec2(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    static final class Vec3 {
        Double x;
        Double y;
        Double z;

        Vec3() {}

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final class Anchor {
        Integer x;
        Integer y;

        Anchor() {}

        Anchor(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class ColorData {
        Integer r;
        Integer g;
        Integer b;
        Double a;

        ColorData() {}

        ColorData(int r, int g, int b, double a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    static final class StateResponse {
        final boolean loggedIn;
        final String authPath;
        final @Nullable String uuid;
        final @Nullable String username;
        final boolean canEditGlobal;
        final List<SetModel> sets;

        StateResponse(boolean loggedIn, String authPath, @Nullable String uuid, @Nullable String username,
                      boolean canEditGlobal, List<SetModel> sets) {
            this.loggedIn = loggedIn;
            this.authPath = authPath;
            this.uuid = uuid;
            this.username = username;
            this.canEditGlobal = canEditGlobal;
            this.sets = sets;
        }
    }
}
