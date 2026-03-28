package codes.antti.auth.common.fabric;

import java.util.logging.Logger;

public class LogWrapper extends Logger {
    private final org.slf4j.Logger slf4j;
    private final String prefix;

    public LogWrapper(org.slf4j.Logger slf4j, String prefix) {
        super(slf4j.getName(), null);
        this.slf4j = slf4j;
        this.prefix = prefix + " ";
    }

    @Override
    public void info(String msg) { slf4j.info(prefix + msg); }

    @Override
    public void warning(String msg) { slf4j.warn(prefix + msg); }

    @Override
    public void severe(String msg) { slf4j.error(prefix + msg); }

    @Override
    public void fine(String msg) { slf4j.debug(prefix + msg); }
}
