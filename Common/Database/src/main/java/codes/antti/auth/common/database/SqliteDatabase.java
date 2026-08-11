package codes.antti.auth.common.database;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

public class SqliteDatabase implements Database {
    private static final int POOL_SIZE = 4;

    private final ArrayBlockingQueue<Connection> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    public SqliteDatabase(@NotNull String path) throws SQLException {
        this(path, DriverManager.getDriver("jdbc:sqlite:" + path));
    }

    public SqliteDatabase(@NotNull String path, @NotNull Driver driver) throws SQLException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("Couldn't create data folder");
        }
        String url = "jdbc:sqlite:" + path;

        for (int i = 0; i < POOL_SIZE; i++) {
            Connection conn = driver.connect(url, new Properties());
            conn.setAutoCommit(true);
            applyPragmas(conn);
            pool.offer(conn);
        }
    }

    private static void applyPragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA synchronous=FULL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA cache_size=-4096");
            stmt.execute("PRAGMA temp_store=MEMORY");
            stmt.execute("PRAGMA mmap_size=4194304");
        }
    }

    private Connection acquire() throws SQLException {
        try {
            return pool.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a connection", e);
        }
    }

    public <T> T query(@NotNull @Language("sql") String sql, @NotNull ResultSetHandler<T> handler, Object... parameters) throws SQLException {
        Connection conn = acquire();
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return handler.handle(rs);
            }
        } finally {
            pool.offer(conn);
        }
    }

    public void update(@NotNull @Language("sql") String sql, Object... parameters) throws SQLException {
        Connection conn = acquire();
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        } finally {
            pool.offer(conn);
        }
    }

    public void close() {
        Connection conn;
        while ((conn = pool.poll()) != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
