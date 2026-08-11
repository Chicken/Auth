package codes.antti.auth.common.database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.logging.Logger;

public final class DatabaseFactory {
    private DatabaseFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Database open(
        @NotNull String databaseUrl,
        @NotNull Path dataPath,
        @Nullable String driverJar,
        @Nullable String driverClass,
        @NotNull Logger logger
    ) throws Exception {
        String absoluteDataPath = dataPath.toAbsolutePath().toString();
        String rawUrl = databaseUrl.replace("{data}", absoluteDataPath);
        Class<? extends Driver> externalDriver = loadDriver(driverJar, driverClass, absoluteDataPath, logger);

        if (rawUrl.startsWith("jdbc:sqlite:")) {
            String path = rawUrl.substring("jdbc:sqlite:".length());
            return externalDriver == null
                ? new SqliteDatabase(path)
                : new SqliteDatabase(path, externalDriver.getDeclaredConstructor().newInstance());
        }

        return externalDriver == null
            ? new HikariDatabase(rawUrl)
            : new HikariDatabase(rawUrl, externalDriver.getDeclaredConstructor().newInstance());
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Class<? extends Driver> loadDriver(
        @Nullable String driverJar,
        @Nullable String driverClass,
        @NotNull String dataPath,
        @NotNull Logger logger
    ) throws Exception {
        if (driverClass == null) {
            if (driverJar != null)
                throw new IllegalArgumentException("driver_class is required when driver_jar is configured");
            return null;
        }

        Class<?> loaded;
        if (driverJar != null) {
            URL jarUrl = new File(driverJar.replace("{data}", dataPath)).toURI().toURL();
            ClassLoader loader = new URLClassLoader(new URL[] {jarUrl}, DatabaseFactory.class.getClassLoader());
            loaded = Class.forName(driverClass, true, loader);
            logger.info("Loaded JDBC driver: " + driverClass + " from " + driverJar);
        } else {
            loaded = Class.forName(driverClass);
            logger.info("Loaded JDBC driver: " + driverClass);
        }

        if (!Driver.class.isAssignableFrom(loaded))
            throw new IllegalArgumentException(driverClass + " does not implement java.sql.Driver");
        return (Class<? extends Driver>) loaded;
    }
}
