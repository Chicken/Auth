package codes.antti.auth.bluemap.markereditor;

import org.jetbrains.annotations.Nullable;

final class ApiException extends Exception {
    final int status;
    final String code;
    final @Nullable String field;

    ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    ApiException(int status, String code, String message, @Nullable String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    static ApiException field(String field, String message) {
        return new ApiException(400, "validation_error", message, field);
    }
}
