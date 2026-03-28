package codes.antti.auth.common.database;

import java.sql.SQLException;

public interface Database {
    <T> T query(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException;
    void update(String sql, Object... params) throws SQLException;
    void close();
}
