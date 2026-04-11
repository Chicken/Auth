package codes.antti.auth.common.fabric;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class FabricConfig {
    private final Map<String, Object> data;

    public FabricConfig(Path configFile, String resourceName) throws IOException {
        if (!Files.exists(configFile)) {
            Files.createDirectories(configFile.getParent());
            try (InputStream in = FabricConfig.class.getResourceAsStream("/" + resourceName)) {
                if (in != null) Files.copy(in, configFile);
            }
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> loaded = yaml.load(in);
            this.data = loaded != null ? loaded : new HashMap<>();
        }
    }

    public String getString(String key, String def) {
        Object val = data.get(key);
        return val != null ? val.toString() : def;
    }

    public int getInt(String key, int def) {
        Object val = data.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = data.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return def;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getStringMap(String key) {
        Object val = data.get(key);
        if (val instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) val;
            Map<String, String> result = new HashMap<>();
            raw.forEach((k, v) -> { if (k != null && v != null) result.put(k.toString(), v.toString()); });
            return result;
        }
        return new HashMap<>();
    }
}
