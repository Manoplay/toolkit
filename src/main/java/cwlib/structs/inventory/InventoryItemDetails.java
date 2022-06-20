package cwlib.structs.inventory;

import cwlib.resources.RTranslationTable;
import cwlib.enums.InventoryObjectSubType;
import cwlib.enums.InventoryObjectType;
import cwlib.types.data.ResourceDescriptor;
import toolkit.utilities.ResourceSystem;
import cwlib.enums.ResourceType;
import cwlib.enums.SlotType;
import cwlib.enums.ToolType;
import cwlib.types.data.Revision;
import cwlib.types.data.SHA1;
import cwlib.types.data.NetworkPlayerID;
import cwlib.structs.slot.SlotID;
import cwlib.io.Serializable;
import cwlib.io.serializer.Serializer;

import java.util.Date;
import java.util.EnumSet;

public class InventoryItemDetails implements Serializable {
    public static int MAX_SIZE = 0x800;
    
    public String translationTag = "";
    public String categoryTag = "";
    public String locationTag = "";
    
    public long dateAdded = new Date().getTime() / 1000;
    public SlotID levelUnlockSlotID = new SlotID();
    public long highlightSound;
    public int colour;
    
    public EnumSet<InventoryObjectType> type = EnumSet.noneOf(InventoryObjectType.class);
    public int subType = InventoryObjectSubType.NONE;
    
    public long titleKey, descriptionKey;
    
    public UserCreatedDetails userCreatedDetails;
    
    public CreationHistory creationHistory;
    public ResourceDescriptor icon;
    
    public InventoryItemPhotoData photoData;
    public EyetoyData eyetoyData;
    
    public short locationIndex = -1, categoryIndex = -1;
    public short primaryIndex;
    public int lastUsed;
    public int numUses;
    public int fluffCost;
    
    public boolean allowEmit;
    public boolean shareable;
    public boolean copyright;
    
    public NetworkPlayerID creator;
    
    public ToolType toolType = ToolType.NONE;
    public byte flags;
    
    public boolean makeSizeProportional = true;
    
    public long location;
    public long category;
    
    public String translatedTitle = "";
    public String translatedDescription;
    public String translatedLocation = "";
    public String translatedCategory = "";

    public InventoryItemDetails serialize(Serializer serializer, Serializable structure) {
        InventoryItemDetails details = 
                (structure == null) ? new InventoryItemDetails() : (InventoryItemDetails) structure;
        
        int head = serializer.getRevision().getVersion();
        
        if (serializer.isWriting() && details.highlightSound != 0)
            serializer.addDependency(new ResourceDescriptor(details.highlightSound, ResourceType.FILENAME));
        
        if (serializer.getRevision().getVersion() > 0x37c) {
            details.dateAdded = serializer.i64d(details.dateAdded);
            details.levelUnlockSlotID = serializer.struct(details.levelUnlockSlotID, SlotID.class);
            details.highlightSound = serializer.u32(details.highlightSound);
            details.colour = serializer.i32(details.colour);
            
            
            if (serializer.isWriting())
                serializer.getOutput().i32(InventoryObjectType.getFlags(details.type));
            else
                details.type = InventoryObjectType.fromFlags(serializer.getInput().i32(), serializer.getRevision());
            
            details.subType = serializer.i32(details.subType);
            
            details.titleKey = serializer.u32(details.titleKey);
            details.descriptionKey = serializer.u32(details.descriptionKey);
            
            details.creationHistory = serializer.reference(details.creationHistory, CreationHistory.class);
            details.icon = serializer.resource(details.icon, ResourceType.TEXTURE, true);
            details.userCreatedDetails = serializer.reference(details.userCreatedDetails, UserCreatedDetails.class);
            details.photoData = serializer.reference(details.photoData, InventoryItemPhotoData.class);
            details.eyetoyData = serializer.reference(details.eyetoyData, EyetoyData.class);
            
            details.locationIndex = serializer.i16(details.locationIndex);
            details.categoryIndex = serializer.i16(details.categoryIndex);
            details.primaryIndex = serializer.i16(details.primaryIndex);
            
            details.creator = serializer.reference(details.creator, NetworkPlayerID.class);
            
            details.toolType = ToolType.getValue(serializer.i8(details.toolType.value));
            details.flags = serializer.i8(details.flags);
            
            if (serializer.getRevision().isAfterVitaRevision(0x7c))
                details.makeSizeProportional = serializer.bool(details.makeSizeProportional);
            
            if (!serializer.isWriting())
                details.updateTranslations();
            
            return details;
        }
        
        if (head < 0x233) {
            if (head < 0x174) {
                serializer.wstr(null); // nameTranslationTag
                serializer.wstr(null); // descTranslationTag
            } else {
                details.translationTag = serializer.str(details.translationTag);
            }

            details.locationIndex = (short) serializer.i32(details.locationIndex, true);
            details.categoryIndex = (short) serializer.i32(details.categoryIndex, true);
            if (head > 0x194)
                details.primaryIndex = (short) serializer.i32(details.primaryIndex, true);
            
            serializer.i32(0, true); // Pad
            
            if (serializer.isWriting)
                serializer.output.i32f(InventoryObjectType.getFlags(details.type));
            else
                details.type = InventoryObjectType.fromFlags(serializer.input.i32f(), serializer.revision);
            details.subType = serializer.i32f(details.subType);
            
            if (head > 0x196)
                details.toolType = ToolType.getValue((byte) serializer.i32f(details.toolType.value));
            details.icon = serializer.resource(details.icon, ResourceType.TEXTURE, true);
            if (head > 0x1c0) {
                details.numUses = serializer.i32f(details.numUses);
                details.lastUsed = serializer.i32f(details.lastUsed);
            }

            if (head > 0x14e)
                details.highlightSound = serializer.u32f(details.highlightSound);
            else
                serializer.str(null); // Path to highlight sound?

            if (head > 0x156)
                details.colour = serializer.i32(details.colour, true);

            if (head > 0x161) {
                details.eyetoyData = serializer.reference(details.eyetoyData, EyetoyData.class);
            }

            if (head > 0x181)
                details.photoData = serializer.reference(details.photoData, InventoryItemPhotoData.class);

            if (head > 0x176) {
                details.levelUnlockSlotID.sl =
                    SlotType.fromValue(
                        serializer.i32(details.levelUnlockSlotID.getSlotType().value)
                    );

                details.levelUnlockSlotID.ID = 
                    serializer.u32f(details.levelUnlockSlotID.ID);
            }

            if (head > 0x181) {
                details.copyright = serializer.bool(details.copyright);
                details.creator = serializer.struct(details.creator, NetworkPlayerID.class);
            }

            if (head > 0x1aa) {
                details.userCreatedDetails = serializer.struct(details.userCreatedDetails, UserCreatedDetails.class);
                if (details.userCreatedDetails != null && 
                        details.userCreatedDetails.title.isEmpty() && 
                        details.userCreatedDetails.description.isEmpty())
                    details.userCreatedDetails = null;
            }

            if (head > 0x1b0)
                details.creationHistory = serializer.struct(details.creationHistory, CreationHistory.class);
            
            if (head > 0x204)
                details.allowEmit = serializer.bool(details.allowEmit);

            if (head > 0x221)
                details.dateAdded = serializer.i64f(details.dateAdded);
            
            if (head > 0x222)
                details.shareable = serializer.bool(details.shareable);
            
            if (!serializer.isWriting)
                details.updateTranslations();

            return details;
        }
        
        details.highlightSound = serializer.u32f(details.highlightSound);

        // NOTE(Aidan): In these older versions of the inventory details,
        // 32 bit values are enforced while still using encoded values elsewhere,
        // so for some structures like SlotID, we need to force it manually.

        details.levelUnlockSlotID.type =
            SlotType.fromValue(
                serializer.i32f(details.levelUnlockSlotID.type.value)
            );

        details.levelUnlockSlotID.ID = 
            serializer.u32f(details.levelUnlockSlotID.ID);

        details.locationIndex = (short) serializer.i32f(details.locationIndex);
        details.categoryIndex = (short) serializer.i32f(details.categoryIndex);
        details.primaryIndex = (short) serializer.i32f(details.primaryIndex);

        details.lastUsed = serializer.i32f(details.lastUsed);
        details.numUses = serializer.i32f(details.numUses);
        if (head > 0x234)
            serializer.i32f(0); // Pad


        details.dateAdded = serializer.i64f(details.dateAdded);
        
        details.fluffCost = serializer.i32f(details.fluffCost);
        
        details.colour = serializer.i32f(details.colour);
        
        if (serializer.isWriting)
            serializer.output.i32f(InventoryObjectType.getFlags(details.type));
        else
            details.type = InventoryObjectType.fromFlags(serializer.input.i32f(), serializer.revision);
        details.subType = serializer.i32f(details.subType);
        details.toolType = ToolType.getValue((byte) serializer.i32f(details.toolType.value));

        details.creator = serializer.struct(details.creator, NetworkPlayerID.class);

        details.allowEmit = serializer.bool(details.allowEmit);
        details.shareable = serializer.bool(details.shareable);
        details.copyright = serializer.bool(details.copyright);
        if (head > 0x334) 
            details.flags = serializer.i8(details.flags);

        if (serializer.getRevision().isAfterLeerdamerRevision(7) || head > 0x2ba) {
            details.titleKey = serializer.u32(details.titleKey);
            details.descriptionKey = serializer.u32(details.descriptionKey);
        } else 
            details.translationTag = serializer.str(details.translationTag);

        details.userCreatedDetails = serializer.struct(details.userCreatedDetails, UserCreatedDetails.class);
        if (details.userCreatedDetails != null && 
                details.userCreatedDetails.name.isEmpty() && 
                details.userCreatedDetails.description.isEmpty())
            details.userCreatedDetails = null;
        
        details.creationHistory = serializer.struct(details.creationHistory, CreationHistory.class);

        details.icon = serializer.resource(details.icon, ResourceType.TEXTURE, true);
        details.photoData = serializer.reference(details.photoData, InventoryItemPhotoData.class);
        details.eyetoyData = serializer.reference(details.eyetoyData, EyetoyData.class);
        
        if (!serializer.isWriting())
            details.updateTranslations();
        
        return details;
    }
    
    public SHA1 generateHashCode(Revision revision) {
        // I wonder how slow this is...
        Serializer serializer = new Serializer(MAX_SIZE, revision, (byte) 0);
        serializer.struct(this, InventoryItemDetails.class);
        return SHA1.fromBuffer(serializer.getBuffer());
    }
    
    private void updateTranslations() {
        if (this.translationTag != null && !this.translationTag.isEmpty()) {
            this.titleKey = 
                    RTranslationTable.makeLamsKeyID(this.translationTag + "_NAME");
            this.descriptionKey = 
                    RTranslationTable.makeLamsKeyID(this.translationTag + "_DESC");
        }
        
        if (ResourceSystem.LAMS != null) {
            if (this.titleKey != 0)
                this.translatedTitle = ResourceSystem.LAMS.translate(this.titleKey);
            if (this.descriptionKey != 0)
                this.translatedDescription = ResourceSystem.LAMS.translate(this.descriptionKey);
        }
    }
}
