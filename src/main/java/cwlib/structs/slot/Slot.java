package cwlib.structs.slot;

import toolkit.utilities.ResourceSystem;
import cwlib.types.data.ResourceDescriptor;
import cwlib.types.data.Revision;
import cwlib.types.data.SHA1;
import cwlib.enums.Branch;
import cwlib.enums.GameMode;
import cwlib.enums.GameProgressionStatus;
import cwlib.enums.LevelType;
import cwlib.enums.ResourceType;
import cwlib.enums.Revisions;
import cwlib.io.Serializable;
import cwlib.io.serializer.Serializer;
import cwlib.types.data.GUID;
import cwlib.types.data.NetworkOnlineID;

import org.joml.Vector4f;

public class Slot implements Serializable {
    public static final int BASE_ALLOCATION_SIZE = 
        (SlotID.BASE_ALLOCATION_SIZE * 3) + (0x20 * 2) + NetworkOnlineID.BASE_ALLOCATION_SIZE + 0x30;
    
    public SlotID id = new SlotID();
    public ResourceDescriptor root;
    public ResourceDescriptor adventure;
    public ResourceDescriptor icon;
    public Vector4f location = new Vector4f().zero();
    public NetworkOnlineID authorID;
    public String authorName;
    public String translationTag;
    public String name;
    public String description;
    public SlotID primaryLinkLevel = new SlotID();
    public SlotID group = new SlotID();
    public boolean initiallyLocked;
    public boolean shareable;
    public GUID backgroundGUID;
    public LevelType developerLevelType = LevelType.MAIN_PATH;
    public GameProgressionStatus gameProgressionState = GameProgressionStatus.NEW_GAME;

    /* LBP2 fields */

    public ResourceDescriptor planetDecorations;
    public Label[] labels;
    public Collectabubble[] collectabubblesRequired;
    public Collectabubble[] collectabubblesContained;
    public boolean isSubLevel;
    public byte minPlayers = 1, maxPlayers = 4;
    public boolean moveRecommended, crossCompatible;
    public boolean showOnPlanet = true;
    public byte livesOverride;

    /* LBP3 fields */
    public boolean enforceMinMaxPlayers;
    public GameMode gameMode = GameMode.NONE;
    public boolean isGameKit;
    public String entranceName;
    public SlotID originalSlotID = new SlotID();
    public byte customBadgeSize = 1;
    public String localPath;
    public String thumbPath;

    /* Vita fields */
    public boolean acingEnabled = true;
    public int[] customRewardEnabled;
    public String[] rewardConditionDescription;
    public int[] customRewardCondition;
    public float[] amountNeededCustomReward;
    public String[] customRewardDescription;

    public boolean containsCollectabubbles;
    public boolean sameScreenGame;
    public int sizeOfResources;
    public int sizeOfSubLevels;

    public SlotID[] subLevels;
    public ResourceDescriptor slotList;
    public short revision;

    // Server specific data
    public int gameVersion;
    public SHA1[] resources;
    
    @SuppressWarnings("unchecked")
    @Override public Slot serialize(Serializer serializer, Serializable structure) {
        Slot slot = (structure == null) ? new Slot() : (Slot) structure;
        Revision revision = serializer.getRevision();
        int version = revision.getVersion();
        int subVersion = revision.getSubVersion();
        
        slot.id = serializer.struct(slot.id, SlotID.class);

        slot.root = serializer.resource(slot.root, ResourceType.LEVEL, true);
        if (subVersion >= Revisions.ADVENTURE)
            slot.adventure = serializer.resource(slot.adventure, ResourceType.LEVEL, true);
        slot.icon = serializer.resource(slot.icon, ResourceType.TEXTURE, true);

        slot.location = serializer.v4(slot.location);

        slot.authorID = serializer.struct(slot.authorID, NetworkOnlineID.class);
        if (version >= 0x13b)
            slot.authorName = serializer.wstr(slot.authorName);

        if (version >= 0x183)
            slot.translationTag = serializer.str(slot.translationTag);

        slot.name = serializer.wstr(slot.name);
        slot.description = serializer.wstr(slot.description);

        slot.primaryLinkLevel = serializer.struct(slot.primaryLinkLevel, SlotID.class);
        if (version >= 0x134)
            slot.group = serializer.struct(slot.group, SlotID.class);

        slot.initiallyLocked = serializer.bool(slot.initiallyLocked);

        if (version > 0x237) {
            slot.shareable = serializer.bool(slot.shareable);
            slot.backgroundGUID = serializer.guid(slot.backgroundGUID);
        }

        if (version > 0x332)
            slot.planetDecorations = serializer.resource(slot.planetDecorations, ResourceType.PLAN, true);

        if (version < 0x188)
            serializer.u8(0); // Unknown
        
        if (version > 0x1de)
            slot.developerLevelType = serializer.enum32(slot.developerLevelType);
        else 
            serializer.bool(false); // isStoryLevel

        if (version > 0x1ad && version < 0x1b9)
            serializer.u8(0); // Unknown
            
        if (version > 0x1b8 && version < 0x36c)
            slot.gameProgressionState = serializer.enum32(slot.gameProgressionState);

        if (version <= 0x2c3) return slot;

        if (version > 0x33b)
            slot.labels = serializer.array(slot.labels, Label.class);

        if (version > 0x2e9)
            slot.collectabubblesRequired = serializer.array(slot.collectabubblesRequired, Collectabubble.class);
        if (version > 0x2f3)
            slot.collectabubblesContained = serializer.array(slot.collectabubblesContained, Collectabubble.class);

        if (version < 0x352) return slot;

        slot.isSubLevel = serializer.bool(slot.isSubLevel);

        if (version < 0x3d0) return slot;

        slot.minPlayers = serializer.i8(slot.minPlayers);
        slot.maxPlayers = serializer.i8(slot.maxPlayers);

        if (subVersion >= Revisions.SLOT_ENFORCE_MINMAX)
            slot.enforceMinMaxPlayers = serializer.bool(slot.enforceMinMaxPlayers);

        if (version >= 0x3d0)
            slot.moveRecommended = serializer.bool(slot.moveRecommended);

        if (version >= 0x3e9)
            slot.crossCompatible = serializer.bool(slot.crossCompatible);

        slot.showOnPlanet = serializer.bool(slot.showOnPlanet);
        slot.livesOverride = serializer.i8(slot.livesOverride);

        if (revision.isVita()) {
            if (revision.has(Branch.DOUBLE11, Revisions.D1_SLOT_REWARDS)) {
                slot.acingEnabled = serializer.bool(slot.acingEnabled);
                slot.customRewardEnabled = serializer.intarray(slot.customRewardEnabled);

                if (!serializer.isWriting()) slot.rewardConditionDescription = new String[serializer.getInput().i32()];
                else serializer.getOutput().i32(slot.rewardConditionDescription.length);
                for (int i = 0; i < slot.rewardConditionDescription.length; ++i)
                    slot.rewardConditionDescription[i] = serializer.wstr(slot.rewardConditionDescription[i]);

                slot.customRewardCondition = serializer.intarray(slot.customRewardCondition);
                slot.amountNeededCustomReward = serializer.floatarray(slot.amountNeededCustomReward);

                if (!serializer.isWriting()) slot.customRewardDescription = new String[serializer.getInput().i32()];
                else serializer.getOutput().i32(slot.customRewardDescription.length);
                for (int i = 0; i < slot.customRewardDescription.length; ++i)
                    slot.customRewardDescription[i] = serializer.wstr(slot.customRewardDescription[i]);
            }

            if (revision.has(Branch.DOUBLE11, Revisions.D1_COLLECTABUBBLES))
                slot.containsCollectabubbles = serializer.bool(slot.containsCollectabubbles);

            if (revision.has(Branch.DOUBLE11, Revisions.D1_SLOT_ENFORCE_MINMAX))
                slot.enforceMinMaxPlayers = serializer.bool(slot.enforceMinMaxPlayers);

            if (revision.has(Branch.DOUBLE11, Revisions.D1_SLOT_SAME_SCREEN))
                slot.sameScreenGame = serializer.bool(slot.sameScreenGame);

            if (revision.has(Branch.DOUBLE11, Revisions.D1_SLOT_DOWNLOAD_DATA)) {
                slot.sizeOfResources = serializer.i32(slot.sizeOfResources);
                slot.sizeOfSubLevels = serializer.i32(slot.sizeOfSubLevels);
                slot.subLevels = serializer.array(slot.subLevels, SlotID.class);
                slot.slotList = serializer.resource(slot.slotList, ResourceType.SLOT_LIST, true);
            }

            if (revision.has(Branch.DOUBLE11, Revisions.D1_SLOT_REVISION))
                slot.revision = serializer.i16(slot.revision);
        }

        if (!revision.isLBP3()) return slot;

        if (subVersion >= Revisions.SLOT_GAME_MODE)
            slot.gameMode = serializer.enum32(slot.gameMode);

        if (subVersion >= Revisions.SLOT_GAME_KIT)
            slot.isGameKit = serializer.bool(slot.isGameKit);

        if (subVersion >= Revisions.SLOT_ENTRANCE_DATA) {
            slot.entranceName = serializer.wstr(slot.entranceName);
            slot.originalSlotID = serializer.struct(slot.originalSlotID, SlotID.class);
        }

        if (subVersion >= Revisions.SLOT_BADGE_SIZE)
            slot.customBadgeSize = serializer.i8(slot.customBadgeSize);

        if (subVersion >= Revisions.SLOT_TRAILER_PATH) {
            slot.localPath = serializer.str(slot.localPath);
            if (subVersion >= Revisions.SLOT_TRAILER_THUMBNAIL)
                slot.thumbPath = serializer.str(slot.thumbPath);
        }

        return slot;
    }

    @Override public int getAllocatedSize() { 
        int size = BASE_ALLOCATION_SIZE;
        if (this.authorName != null)
            size += (this.authorName.length() * 2);
        if (this.translationTag != null)
            size += (this.translationTag.length() * 2);
        if (this.name != null)
            size += (this.name.length() * 2);
        if (this.description != null)
            size += (this.description.length() * 2);
        return size;
    }

    @Override public String toString() {
        if (this.translationTag != null && !this.translationTag.isEmpty()) {
            if (ResourceSystem.getLAMS() == null) return this.translationTag;
            String translated = ResourceSystem.getLAMS().translate(this.translationTag + "_NAME");
            if (translated != null) return translated;
            return this.translationTag;
        }
        if (this.name == null || this.name.isEmpty())
            return "Unnamed Level";
        return this.name;
    }
}
