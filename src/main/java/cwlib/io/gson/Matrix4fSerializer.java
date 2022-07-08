package cwlib.io.gson;

import java.lang.reflect.Type;
import java.nio.FloatBuffer;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.joml.Matrix4f;

public class Matrix4fSerializer implements JsonSerializer<Matrix4f>, JsonDeserializer<Matrix4f> {
    @Override public Matrix4f deserialize(JsonElement je, Type type, JsonDeserializationContext jdc) throws JsonParseException {
        JsonArray array = je.getAsJsonArray();
        if (array.size() != 16)
            throw new JsonParseException("Matrix4f serializable object must have 16 elements!");
        FloatBuffer BUFFER = FloatBuffer.allocate(16);
        for (JsonElement element : array)
            BUFFER.put(element.getAsFloat());
        return new Matrix4f(BUFFER);
    }

    @Override public JsonElement serialize(Matrix4f matrix, Type type, JsonSerializationContext jsc) {

        JsonArray array = new JsonArray(16);
        float[] values = new float[16];
        matrix.get(values);
        for (int i = 0; i < 16; ++i)
            array.add(values[i]);
        return array;
    }
}
