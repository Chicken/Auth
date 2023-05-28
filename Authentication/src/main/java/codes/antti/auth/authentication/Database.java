package codes.antti.auth.authentication;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;

public class Database {
    Connection conn;
    public Database(@NotNull String path) throws SQLException {
        File file = new File(path);
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new RuntimeException("Couldn't create data folder");
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        conn.setAutoCommit(true);
    }

    public ResultSet query(@NotNull @Language("sql") String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = conn.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        return statement.executeQuery();
    }

    public void update(@NotNull @Language("sql") String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = conn.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        statement.executeUpdate();
    }

    public void close() {
        try { this.conn.commit(); } catch (SQLException ignored) {}
        try { this.conn.close(); } catch (SQLException ignored) {}
    }
}
