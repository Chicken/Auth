package codes.antti.auth.bluemap.markereditor;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static codes.antti.auth.bluemap.markereditor.MarkerModels.*;

final class MarkerCodec {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern STYLE_CLASS_PATTERN = Pattern.compile("-?[_a-zA-Z]+[_a-zA-Z0-9-]*");
    private static final Set<String> MARKER_TYPES = Set.of("poi", "html", "line", "shape", "extrude");
    private static final int MAX_POINTS = 4096;
    private static final int MAX_HOLES = 64;

    private MarkerCodec() {}

    static Scope normalizeSet(SetInput set, Scope existingScope) throws ApiException {
        if (set == null) throw ApiException.field("body", "Marker set is required");
        validateKey(set.key, "key");
        set.label = string(set.label, "label", 256, true);
        set.toggleable = set.toggleable == null ? Boolean.TRUE : set.toggleable;
        set.defaultHidden = set.defaultHidden == null ? Boolean.FALSE : set.defaultHidden;
        set.sorting = set.sorting == null ? 0 : set.sorting;

        Scope scope = set.scope == null && existingScope != null ? existingScope : Scope.parse(set.scope);
        if (existingScope != null && scope != existingScope)
            throw ApiException.field("scope", "Scope cannot be changed");
        set.scope = scope.jsonName();
        return scope;
    }

    static MarkerData normalizeMarker(MarkerData input) throws ApiException {
        if (input == null) throw ApiException.field("body", "Marker is required");
        validateKey(input.key, "key");
        if (!MARKER_TYPES.contains(input.type))
            throw ApiException.field("type", "Type must be poi, html, line, shape, or extrude");

        MarkerData marker = new MarkerData();
        marker.key = input.key;
        marker.type = input.type;
        marker.label = string(input.label, "label", 256, true);
        marker.sorting = input.sorting == null ? 0 : input.sorting;
        marker.listed = input.listed == null ? Boolean.TRUE : input.listed;
        marker.minDistance = input.minDistance == null ? 0d : finite(input.minDistance, "minDistance");
        marker.maxDistance = input.maxDistance == null ? 10_000_000d : finite(input.maxDistance, "maxDistance");
        if (marker.minDistance < 0) throw ApiException.field("minDistance", "Minimum distance cannot be negative");
        if (marker.maxDistance < marker.minDistance)
            throw ApiException.field("maxDistance", "Maximum distance must not be smaller than minimum distance");

        switch (marker.type) {
            case "poi" -> normalizePoi(input, marker);
            case "html" -> normalizeHtml(input, marker);
            case "line" -> normalizeLine(input, marker);
            case "shape" -> normalizeShape(input, marker);
            case "extrude" -> normalizeExtrude(input, marker);
            default -> throw new IllegalStateException("Unhandled marker type");
        }

        return marker;
    }

    private static void normalizePoi(MarkerData input, MarkerData marker) throws ApiException {
        marker.positionMode = pointPositionMode(input.positionMode);
        marker.position = vec3(input.position, "position");
        marker.detail = optionalString(input.detail, marker.label, "detail", 32768);
        marker.icon = optionalString(input.icon, "assets/poi.svg", "icon", 2048);
        marker.anchor = anchor(input.anchor, new Anchor(25, 45));
        marker.styleClasses = styleClasses(input.styleClasses);
    }

    private static void normalizeHtml(MarkerData input, MarkerData marker) throws ApiException {
        marker.positionMode = pointPositionMode(input.positionMode);
        marker.position = vec3(input.position, "position");
        marker.html = string(input.html, "html", 32768, true);
        marker.anchor = anchor(input.anchor, new Anchor(0, 0));
        marker.styleClasses = styleClasses(input.styleClasses);
    }

    private static void normalizeLine(MarkerData input, MarkerData marker) throws ApiException {
        marker.line = line(input.line);
        normalizeObject(input, marker);
        marker.position = objectPosition(input.position, marker.positionMode, lineCenter(marker.line));
        marker.lineColor = color(input.lineColor, new ColorData(255, 0, 0, 1));
    }

    private static void normalizeShape(MarkerData input, MarkerData marker) throws ApiException {
        marker.shape = shape(input.shape, "shape");
        marker.holes = holes(input.holes);
        marker.shapeY = finiteFloat(input.shapeY, "shapeY");
        normalizeObject(input, marker);
        marker.position = objectPosition(input.position, marker.positionMode, shapeCenter(marker.shape, marker.shapeY));
        marker.lineColor = color(input.lineColor, new ColorData(255, 0, 0, 1));
        marker.fillColor = color(input.fillColor, new ColorData(200, 0, 0, 0.3));
    }

    private static void normalizeExtrude(MarkerData input, MarkerData marker) throws ApiException {
        marker.shape = shape(input.shape, "shape");
        marker.holes = holes(input.holes);
        marker.minY = finiteFloat(input.minY, "minY");
        marker.maxY = finiteFloat(input.maxY, "maxY");
        if (marker.maxY < marker.minY)
            throw ApiException.field("maxY", "Maximum Y must not be smaller than minimum Y");
        normalizeObject(input, marker);
        marker.position = objectPosition(
            input.position, marker.positionMode, shapeCenter(marker.shape, (marker.minY + marker.maxY) * 0.5)
        );
        marker.lineColor = color(input.lineColor, new ColorData(255, 0, 0, 1));
        marker.fillColor = color(input.fillColor, new ColorData(200, 0, 0, 0.3));
    }

    private static void normalizeObject(MarkerData input, MarkerData marker) throws ApiException {
        marker.positionMode = objectPositionMode(input.positionMode);
        marker.detail = optionalString(input.detail, marker.label, "detail", 32768);
        marker.link = input.link == null || input.link.isEmpty() ? null : string(input.link, "link", 2048, true);
        marker.newTab = marker.link != null && Boolean.TRUE.equals(input.newTab);
        marker.depthTest = input.depthTest == null ? Boolean.TRUE : input.depthTest;
        marker.lineWidth = input.lineWidth == null ? 2 : input.lineWidth;
        if (marker.lineWidth < 0 || marker.lineWidth > 64)
            throw ApiException.field("lineWidth", "Line width must be between 0 and 64");
    }

    static Marker buildMarker(MarkerData data) {
        Vector3d position = vector3(data.position);
        return switch (data.type) {
            case "poi" -> POIMarker.builder()
                .label(data.label).position(position).sorting(data.sorting).listed(data.listed)
                .minDistance(data.minDistance).maxDistance(data.maxDistance).detail(data.detail)
                .icon(data.icon, new Vector2i(data.anchor.x, data.anchor.y))
                .styleClasses(data.styleClasses.toArray(String[]::new)).build();
            case "html" -> HtmlMarker.builder()
                .label(data.label).position(position).sorting(data.sorting).listed(data.listed)
                .minDistance(data.minDistance).maxDistance(data.maxDistance).html(data.html)
                .anchor(new Vector2i(data.anchor.x, data.anchor.y))
                .styleClasses(data.styleClasses.toArray(String[]::new)).build();
            case "line" -> buildLine(data);
            case "shape" -> buildShape(data);
            case "extrude" -> buildExtrude(data);
            default -> throw new IllegalArgumentException("Unknown marker type: " + data.type);
        };
    }

    private static LineMarker buildLine(MarkerData data) {
        LineMarker.Builder builder = LineMarker.builder()
            .label(data.label).line(new Line(data.line.stream().map(MarkerCodec::vector3).toList()))
            .sorting(data.sorting).listed(data.listed).minDistance(data.minDistance).maxDistance(data.maxDistance)
            .detail(data.detail).depthTestEnabled(data.depthTest).lineWidth(data.lineWidth)
            .lineColor(toColor(data.lineColor));
        if ("manual".equals(data.positionMode)) builder.position(vector3(data.position));
        if (data.link != null) builder.link(data.link, data.newTab);
        return builder.build();
    }

    private static ShapeMarker buildShape(MarkerData data) {
        ShapeMarker.Builder builder = ShapeMarker.builder()
            .label(data.label).shape(toShape(data.shape), data.shapeY.floatValue())
            .holes(data.holes.stream().map(MarkerCodec::toShape).toArray(Shape[]::new))
            .sorting(data.sorting).listed(data.listed).minDistance(data.minDistance).maxDistance(data.maxDistance)
            .detail(data.detail).depthTestEnabled(data.depthTest).lineWidth(data.lineWidth)
            .lineColor(toColor(data.lineColor)).fillColor(toColor(data.fillColor));
        if ("manual".equals(data.positionMode)) builder.position(vector3(data.position));
        if (data.link != null) builder.link(data.link, data.newTab);
        return builder.build();
    }

    private static ExtrudeMarker buildExtrude(MarkerData data) {
        ExtrudeMarker.Builder builder = ExtrudeMarker.builder()
            .label(data.label).shape(toShape(data.shape), data.minY.floatValue(), data.maxY.floatValue())
            .holes(data.holes.stream().map(MarkerCodec::toShape).toArray(Shape[]::new))
            .sorting(data.sorting).listed(data.listed).minDistance(data.minDistance).maxDistance(data.maxDistance)
            .detail(data.detail).depthTestEnabled(data.depthTest).lineWidth(data.lineWidth)
            .lineColor(toColor(data.lineColor)).fillColor(toColor(data.fillColor));
        if ("manual".equals(data.positionMode)) builder.position(vector3(data.position));
        if (data.link != null) builder.link(data.link, data.newTab);
        return builder.build();
    }

    private static Shape toShape(List<Vec2> points) {
        return new Shape(points.stream().map(p -> new Vector2d(p.x, p.z)).toList());
    }

    private static Vector3d vector3(Vec3 value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    private static Color toColor(ColorData value) {
        return new Color(value.r, value.g, value.b, value.a.floatValue());
    }

    private static String pointPositionMode(String value) throws ApiException {
        if (value == null || "manual".equals(value)) return "manual";
        throw ApiException.field("positionMode", "Point markers require manual position mode");
    }

    private static String objectPositionMode(String value) throws ApiException {
        if (value == null || "center".equals(value)) return "center";
        if ("manual".equals(value)) return "manual";
        throw ApiException.field("positionMode", "Position mode must be center or manual");
    }

    private static Vec3 objectPosition(Vec3 requested, String mode, Vec3 calculated) throws ApiException {
        return "manual".equals(mode) ? vec3(requested, "position") : calculated;
    }

    private static Vec3 lineCenter(List<Vec3> line) {
        double minX = line.getFirst().x, minY = line.getFirst().y, minZ = line.getFirst().z;
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (Vec3 point : line) {
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }

    private static Vec3 shapeCenter(List<Vec2> shape, double y) {
        double minX = shape.getFirst().x, minZ = shape.getFirst().z;
        double maxX = minX, maxZ = minZ;
        for (Vec2 point : shape) {
            minX = Math.min(minX, point.x); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxZ = Math.max(maxZ, point.z);
        }
        return new Vec3((minX + maxX) * 0.5, y, (minZ + maxZ) * 0.5);
    }

    private static List<Vec3> line(List<Vec3> values) throws ApiException {
        if (values == null || values.size() < 2) throw ApiException.field("line", "A line requires at least 2 points");
        if (values.size() > MAX_POINTS) throw ApiException.field("line", "Line has too many points");
        List<Vec3> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) result.add(vec3(values.get(i), "line[" + i + "]"));
        return result;
    }

    private static List<Vec2> shape(List<Vec2> values, String field) throws ApiException {
        if (values == null || values.size() < 3) throw ApiException.field(field, "A shape requires at least 3 points");
        if (values.size() > MAX_POINTS) throw ApiException.field(field, "Shape has too many points");
        List<Vec2> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) result.add(vec2(values.get(i), field + "[" + i + "]"));
        return result;
    }

    private static List<List<Vec2>> holes(List<List<Vec2>> values) throws ApiException {
        if (values == null) return new ArrayList<>();
        if (values.size() > MAX_HOLES) throw ApiException.field("holes", "Marker has too many holes");
        List<List<Vec2>> result = new ArrayList<>(values.size());
        int points = 0;
        for (int i = 0; i < values.size(); i++) {
            List<Vec2> hole = shape(values.get(i), "holes[" + i + "]");
            points += hole.size();
            if (points > MAX_POINTS) throw ApiException.field("holes", "Marker holes have too many points");
            result.add(hole);
        }
        return result;
    }

    private static Vec2 vec2(Vec2 value, String field) throws ApiException {
        if (value == null) throw ApiException.field(field, "Point is required");
        return new Vec2(finite(value.x, field + ".x"), finite(value.z, field + ".z"));
    }

    private static Vec3 vec3(Vec3 value, String field) throws ApiException {
        if (value == null) throw ApiException.field(field, "Position is required");
        return new Vec3(
            finite(value.x, field + ".x"), finite(value.y, field + ".y"), finite(value.z, field + ".z")
        );
    }

    private static Anchor anchor(Anchor value, Anchor fallback) throws ApiException {
        if (value == null) return fallback;
        if (value.x == null) throw ApiException.field("anchor.x", "Anchor X is required");
        if (value.y == null) throw ApiException.field("anchor.y", "Anchor Y is required");
        return new Anchor(value.x, value.y);
    }

    private static List<String> styleClasses(List<String> values) throws ApiException {
        if (values == null) return new ArrayList<>();
        if (values.size() > 32) throw ApiException.field("styleClasses", "Too many style classes");
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || !STYLE_CLASS_PATTERN.matcher(value).matches())
                throw ApiException.field("styleClasses", "Invalid CSS class");
            result.add(value);
        }
        return result;
    }

    private static ColorData color(ColorData value, ColorData fallback) throws ApiException {
        if (value == null) return fallback;
        if (value.r == null || value.r < 0 || value.r > 255) throw ApiException.field("color.r", "Red must be between 0 and 255");
        if (value.g == null || value.g < 0 || value.g > 255) throw ApiException.field("color.g", "Green must be between 0 and 255");
        if (value.b == null || value.b < 0 || value.b > 255) throw ApiException.field("color.b", "Blue must be between 0 and 255");
        double alpha = finite(value.a, "color.a");
        if (alpha < 0 || alpha > 1) throw ApiException.field("color.a", "Alpha must be between 0 and 1");
        return new ColorData(value.r, value.g, value.b, alpha);
    }

    private static double finite(Double value, String field) throws ApiException {
        if (value == null || !Double.isFinite(value)) throw ApiException.field(field, "A finite number is required");
        return value;
    }

    private static double finiteFloat(Double value, String field) throws ApiException {
        double number = finite(value, field);
        if (Math.abs(number) > Float.MAX_VALUE) throw ApiException.field(field, "Number is out of range");
        return number;
    }

    private static String string(String value, String field, int maxLength, boolean allowEmpty) throws ApiException {
        if (value == null) throw ApiException.field(field, "Value is required");
        if (!allowEmpty && value.isEmpty()) throw ApiException.field(field, "Value cannot be empty");
        if (value.length() > maxLength) throw ApiException.field(field, "Value is too long");
        return value;
    }

    private static String optionalString(String value, String fallback, String field, int maxLength) throws ApiException {
        return value == null ? fallback : string(value, field, maxLength, true);
    }

    static void validateKey(String value, String field) throws ApiException {
        if (value == null || !KEY_PATTERN.matcher(value).matches())
            throw ApiException.field(field, "Use 1-64 letters, numbers, dots, underscores, or hyphens; start with a letter or number");
    }
}
