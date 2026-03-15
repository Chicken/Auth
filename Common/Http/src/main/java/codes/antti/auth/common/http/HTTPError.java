package codes.antti.auth.common.http;

import lombok.Getter;

public class HTTPError extends Exception {
    @Getter
    private final int statusCode;

    public HTTPError(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
