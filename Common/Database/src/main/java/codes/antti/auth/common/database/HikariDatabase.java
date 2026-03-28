package codes.antti.auth.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class HikariDatabase implements Database {
    private final HikariDataSource ds;

    public HikariDatabase(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        this.ds = new HikariDataSource(config);
    }

    public HikariDatabase(String jdbcUrl, Driver driver) {
        HikariConfig config = new HikariConfig();
        config.setDataSource(new DriverDataSource(driver, jdbcUrl));
        this.ds = new HikariDataSource(config);
    }

    private static final class DriverDataSource implements DataSource {
        private final Driver driver;
        private final String url;

        DriverDataSource(Driver driver, String url) {
            this.driver = driver;
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return driver.connect(url, new Properties());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            return driver.connect(url, props);
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Override
    public <T> T query(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                return handler.handle(rs);
            }
        }
    }

    @Override
    public void update(String sql, Object... params) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        ds.close();
    }
}
