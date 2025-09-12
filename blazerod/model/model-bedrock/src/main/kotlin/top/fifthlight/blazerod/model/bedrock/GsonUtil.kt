@file:Suppress("NOTHING_TO_INLINE")

package top.fifthlight.blazerod.model.bedrock

import com.google.gson.stream.JsonReader
import org.joml.Vector2f
import org.joml.Vector3f

inline fun JsonReader.with(block: JsonReader.() -> Unit) {
    block()
}

inline fun JsonReader.obj(onEntry: JsonReader.(key: String) -> Unit) {
    beginObject()
    while (hasNext()) {
        onEntry(nextName())
    }
    endObject()
}

inline fun JsonReader.array(onEntry: JsonReader.() -> Unit) {
    beginArray()
    while (hasNext()) {
        onEntry()
    }
    endArray()
}

inline fun JsonReader.vec3(vector3f: Vector3f) {
    beginArray()
    vector3f.x = nextDouble().toFloat()
    vector3f.y = nextDouble().toFloat()
    vector3f.z = nextDouble().toFloat()
    endArray()
}

inline fun JsonReader.vec2(vector2f: Vector2f) {
    beginArray()
    vector2f.x = nextDouble().toFloat()
    vector2f.y = nextDouble().toFloat()
    endArray()
}
