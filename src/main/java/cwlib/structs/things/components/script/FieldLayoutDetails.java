package cwlib.structs.things.components.script;

import java.util.EnumSet;

import cwlib.enums.BuiltinType;
import cwlib.enums.MachineType;
import cwlib.enums.ModifierType;
import cwlib.enums.ScriptObjectType;
import cwlib.io.Serializable;
import cwlib.io.serializer.Serializer;

public class FieldLayoutDetails implements Serializable {
    public static final int BASE_ALLOCATION_SIZE = 0x20;

    public String name;
    public EnumSet<ModifierType> modifiers = EnumSet.noneOf(ModifierType.class);;
    public MachineType machineType = MachineType.VOID;
    public BuiltinType fishType = BuiltinType.VOID;
    public byte dimensionCount;
    public MachineType arrayBaseMachineType = MachineType.VOID;
    public int instanceOffset;
    
    public Object value;
    public ScriptObjectType type = ScriptObjectType.NULL;

    @SuppressWarnings("unchecked")
    @Override public FieldLayoutDetails serialize(Serializer serializer, Serializable structure) {
        FieldLayoutDetails details = (structure == null) ? new FieldLayoutDetails() : (FieldLayoutDetails) structure;

        int version = serializer.getRevision().getVersion();

        details.name = serializer.str(details.name);

        if (serializer.isWriting()) {
            short flags = ModifierType.getFlags(details.modifiers);
            if (version >= 0x3d9) serializer.getOutput().i16(flags);
            else serializer.getOutput().i32(flags);
        } else {
            int flags = (version >= 0x3d9) ? serializer.getInput().i16() : serializer.getInput().i32();
            details.modifiers = ModifierType.fromValue(flags);
        }

        details.machineType = serializer.enum32(details.machineType);
        if (version >= 0x145)
            details.fishType = serializer.enum32(details.fishType);

        details.dimensionCount = serializer.i8(details.dimensionCount);
        details.arrayBaseMachineType = serializer.enum32(details.arrayBaseMachineType);

        details.instanceOffset = serializer.i32(details.instanceOffset);

        return details;
    }

    @Override public int getAllocatedSize() { 
        int size = FieldLayoutDetails.BASE_ALLOCATION_SIZE; 
        if (this.name != null) size += (this.name.length());
        return size;
    }

    @Override public String toString() {
        return String.format("%s %s %s", this.modifiers, this.machineType, this.name);
    }
}