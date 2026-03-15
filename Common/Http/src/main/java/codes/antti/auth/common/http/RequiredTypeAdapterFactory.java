package codes.antti.auth.common.http;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Field;

public class RequiredTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        if (!hasRequiredFields(type.getRawType())) return delegate;

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                T obj = delegate.read(in);
                if (obj == null) return null;

                Class<?> clazz = obj.getClass();
                for (Field field : clazz.getDeclaredFields()) {
                    if (!field.isAnnotationPresent(Required.class)) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        if (value == null) throw new JsonParseException(
                            "Required field '" + field.getName() + "' is missing or null in " + clazz.getSimpleName()
                        );
                    } catch (IllegalAccessException ignored) {}
                }
                return obj;
            }
        };
    }

    private boolean hasRequiredFields(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Required.class)) return true;
        }
        return false;
    }
}
