package cwlib.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import cwlib.enums.Branch;
import cwlib.enums.ResourceType;
import cwlib.enums.Revisions;
import cwlib.io.gson.GUIDSerializer;
import cwlib.io.gson.ResourceSerializer;
import cwlib.io.gson.GsonResourceType;
import cwlib.io.gson.GsonRevision;
import cwlib.io.gson.GsonRevisions;
import cwlib.io.gson.Matrix4fSerializer;
import cwlib.io.gson.NetworkOnlineIDSerializer;
import cwlib.io.gson.NetworkPlayerIDSerializer;
import cwlib.io.gson.PatchSerializer;
import cwlib.io.gson.SHA1Serializer;
import cwlib.io.gson.ScriptObjectSerializer;
import cwlib.io.gson.SlotIDSerializer;
import cwlib.io.gson.ThingSerializer;
import cwlib.io.gson.Vector2fSerializer;
import cwlib.io.gson.Vector3fSerializer;
import cwlib.io.gson.Vector4fSerializer;
import cwlib.io.gson.WrappedResourceSerializer;
import cwlib.io.gson.CreationHistorySerializer;
import cwlib.io.gson.FieldSerializer;
import cwlib.types.data.NetworkOnlineID;
import cwlib.types.data.NetworkPlayerID;
import cwlib.types.data.Revision;
import cwlib.types.data.ResourceDescriptor;
import cwlib.structs.slot.SlotID;
import cwlib.structs.things.Thing;
import cwlib.structs.things.components.script.FieldLayoutDetails;
import cwlib.structs.things.components.script.ScriptObject;
import cwlib.types.Resource;
import cwlib.types.data.GUID;
import cwlib.types.data.SHA1;
import cwlib.types.mods.patches.ModPatch;
import executables.Jsoninator.WrappedResource;
import cwlib.structs.inventory.CreationHistory;

public final class GsonUtils {
    // public static Revision REVISION = new Revision(Branch.MIZUKI.getHead(), Branch.MIZUKI.getID(), Branch.MIZUKI.getHead());
    public static Revision REVISION = new Revision(0x272, 0x4c44, 0x0017);

    public static final HashMap<Integer, Thing> THINGS = new HashMap<>();
    public static final HashSet<Integer> UIDs = new HashSet<>();


    /**
     * Default GSON serializer object with type-adapters
     * pre-set.
     */
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeSpecialFloatingPointValues()
        .serializeNulls()
        .setExclusionStrategies(new ExclusionStrategy() {
            @Override public boolean shouldSkipField(FieldAttributes field) {
                boolean skip = false;
                
                if (field.getAnnotation(GsonRevision.class) != null) {
                    GsonRevision revision = field.getAnnotation(GsonRevision.class);
                    int head = (revision.lbp3()) ? REVISION.getSubVersion() : REVISION.getVersion();
                    
                    if (revision.branch() != -1 && REVISION.getBranchID() != revision.branch()) skip = true;
                    if (revision.max() != -1 && head > revision.max()) skip = true;
                    if (revision.min() != -1 && head < revision.min()) skip = true;
                }

                if (field.getAnnotation(GsonRevisions.class) != null) {
                    GsonRevision[] revisions = field.getAnnotation(GsonRevisions.class).value();
                    boolean anyTrue = false;
                    for (GsonRevision revision : revisions) {
                        int head = (revision.lbp3()) ? REVISION.getSubVersion() : REVISION.getVersion();

                        boolean max = ((revision.max() == -1) || (revision.max() >= head));
                        boolean min = ((revision.min() == -1) || (revision.min() <= head));
                        boolean branch = ((revision.branch() == -1) || (revision.branch() == REVISION.getBranchID()));

                        if (max && min && branch) anyTrue = true;
                    }
                    skip = !anyTrue;
                }

                return skip;
            }
            
            @Override public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }   
        })
        .registerTypeAdapter(CreationHistory.class, new CreationHistorySerializer())
        .registerTypeAdapter(ModPatch.class, new PatchSerializer())
        .registerTypeAdapter(Vector2f.class, new Vector2fSerializer())
        .registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
        .registerTypeAdapter(Vector4f.class, new Vector4fSerializer())
        .registerTypeAdapter(Matrix4f.class, new Matrix4fSerializer())
        .registerTypeAdapter(SHA1.class, new SHA1Serializer())
        .registerTypeAdapter(SlotID.class, new SlotIDSerializer())
        .registerTypeAdapter(GUID.class, new GUIDSerializer())
        .registerTypeAdapter(NetworkPlayerID.class, new NetworkPlayerIDSerializer())
        .registerTypeAdapter(NetworkOnlineID.class, new NetworkOnlineIDSerializer())
        .registerTypeAdapter(Thing.class, new ThingSerializer())
        .registerTypeAdapter(ResourceDescriptor.class, new ResourceSerializer())
        .registerTypeAdapter(FieldLayoutDetails.class, new FieldSerializer())
        .registerTypeAdapter(WrappedResource.class, new WrappedResourceSerializer())
        .registerTypeAdapter(ScriptObject.class, new ScriptObjectSerializer())
        .create();

    /**
     * Deserializes a JSON string to an object.
     * @param <T> Type to deserialize
     * @param json JSON object to deserialize
     * @param clazz Class to deserialize
     * @return Deserialized object
     */
    public static <T> T fromJSON(String json, Class<T> clazz) { 
        THINGS.clear();
        UIDs.clear();
        return GSON.fromJson(json, clazz);
    }

    /**
     * Serializes an object to a JSON string.
     * @param object Object to serialize
     * @return Serialized JSON string
     */
    public static String toJSON(Object object) {
        THINGS.clear();
        UIDs.clear();
        return GSON.toJson(object);
    }

    public static void main(String[] args) {
        System.out.println(REVISION);
    }
}
