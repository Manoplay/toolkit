package cwlib.structs.things;

import java.util.Arrays;

import cwlib.enums.Branch;
import cwlib.enums.Part;
import cwlib.enums.PartHistory;
import cwlib.enums.Revisions;
import cwlib.ex.SerializationException;
import cwlib.io.Serializable;
import cwlib.io.serializer.Serializer;
import cwlib.structs.things.parts.*;
import cwlib.types.data.GUID;
import cwlib.types.data.Revision;

/**
 * Represents an object in the game world.
 */
public class Thing implements Serializable {
    public static final int BASE_ALLOCATION_SIZE = 0x100;

    public int UID;
    public Thing world;
    public Thing parent;
    public Thing groupHead;
    public Thing oldEmitter;

    public short createdBy, changedBy;
    public boolean isStamping;
    public GUID planGUID;
    public boolean hidden;
    public byte flags, extraFlags;

    private Serializable[] parts = new Serializable[0x36];

    @SuppressWarnings("unchecked")
    @Override public Thing serialize(Serializer serializer, Serializable structure) {
        Thing thing = (structure == null) ? new Thing() : (Thing) structure;

        Revision revision = serializer.getRevision();
        int version = revision.getVersion();
        int subVersion = revision.getSubVersion();

        // Test serialization marker.
        if (version >= Revisions.THING_TEST_MARKER || revision.has(Branch.LEERDAMMER, Revisions.LD_TEST_MARKER))
            serializer.u8(0xAA);

        if (version < 0x1fd)
            thing.world = serializer.reference(thing.world, Thing.class);
        if (version < 0x27f) {
            thing.parent = serializer.reference(thing.parent, Thing.class);
            thing.UID = serializer.i32(thing.UID);
        } else {
            thing.UID = serializer.i32(thing.UID);
            thing.parent = serializer.reference(thing.parent, Thing.class);
        }

        thing.groupHead = serializer.reference(thing.groupHead, Thing.class);

        if (version > 0x1c6)
            thing.oldEmitter = serializer.reference(thing.oldEmitter, Thing.class);

        if (version >= 0x1a6 && version < 0x1bc) {
            int size = serializer.i32(0);
            // pjoint list
            if (size != 0)
                throw new SerializationException("Joint list is not supported!");
        }
        
        if (version > 0x213) {
            thing.createdBy = serializer.i16(thing.createdBy);
            thing.changedBy = serializer.i16(thing.changedBy);
        }

        if (version < 0x341) {
            if (version > 0x21a) 
                thing.isStamping = serializer.bool(thing.isStamping);
            if (version >= 0x254)
                thing.planGUID = serializer.guid(thing.planGUID);
            if (version >= 0x2f2)
                thing.hidden = serializer.bool(thing.hidden);
        } else {
            if (version >= 0x254)
                thing.planGUID = serializer.guid(thing.planGUID);
            if (version >= 0x341)
                thing.flags = serializer.i8(thing.flags);
            if (subVersion >= 0x110)
                thing.extraFlags = serializer.i8(thing.extraFlags);
        }

        boolean isCompressed = (version >= 0x297 || revision.has(Branch.LEERDAMMER, Revisions.LD_RESOURCES));
        
        int partsRevision = PartHistory.STREAMING_HINT;
        long flags = -1;

        if (serializer.isWriting()) {
            serializer.log("generating flags");
            Part lastPart = null;
            if (isCompressed) flags = 0;
            for (Part part : Part.values()) {
                if (this.parts[part.getIndex()] != null) {
                    flags |= (1l << part.getIndex());
                    lastPart = part;
                }
            }
            partsRevision = lastPart.getVersion();
        }

        partsRevision = serializer.s32(partsRevision);
        if (isCompressed)
            flags = serializer.i64(flags);
        
        System.out.println(Arrays.toString(Part.fromFlags(flags)));

        // if (serializer.isWriting()) {
        //     throw new SerializationException("you thought you would win?");
        // }
        
        /* Reflection magic! I really didn't want to write all this */
        for (Part part : Part.values()) {
            if (!part.serialize(this.parts, partsRevision, flags, serializer))
                throw new SerializationException(part.name() + " failed to serialize!");
            if (part.hasPart(partsRevision, flags))
                System.out.println(part.name() + ": " + serializer.getOffset());
        }
        
        return thing;
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getPart(Part part) { return (T) this.parts[part.getIndex()]; }

    @Override public int getAllocatedSize() {  return BASE_ALLOCATION_SIZE;  }
}
