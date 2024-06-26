package cwlib.singleton;

import cwlib.types.data.GUID;
import cwlib.types.data.ResourceDescriptor;
import cwlib.enums.DatabaseType;
import cwlib.io.Resource;
import cwlib.io.Serializable;
import cwlib.resources.RTranslationTable;
import cwlib.types.data.SHA1;
import cwlib.types.swing.FileData;
import cwlib.types.swing.FileModel;
import cwlib.types.swing.FileNode;
import cwlib.util.FileIO;
import cwlib.util.Nodes;
import cwlib.types.SerializedResource;
import cwlib.types.archives.Fart;
import cwlib.types.data.ResourceInfo;
import cwlib.types.databases.FileEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 * Global utilities for working with
 * loaded databases.
 */
public class ResourceSystem
{
    // These are only temporarily here until, well, if, I
    // completely untangle this class from the GUI mode.
    public static Runnable TriggerWorkSpaceUpdate;
    public static Supplier<Fart[]> GetSelectedCaches;
    public static Consumer<JTree> TreeSelectionListener;


    public static class ResourceLogLevel
    {
        public static final int NONE = 0;
        public static final int INFO = 1;
        public static final int ERROR = 2;
        public static final int WARNING = 3;
        public static final int DEBUG = 4;
        public static final int SERIALIZER_TRACE = 5;
    }

    public static class ResourceLogChannel
    {
        public static final String RESOURCE = "ResourceSystem";
        public static final String SERIALIZER = "Serializer";
    }

    public static int LOG_LEVEL = ResourceLogLevel.INFO;
    public static final int MAX_CACHE_ENTRIES = 16;
    /**
     * Resource info cache, gets cleaned when it reaches
     * max capacity
     */
    public static final FileNode[] CACHE = new FileNode[MAX_CACHE_ENTRIES];
    public static int CACHE_INDEX = 0;

    public static void clearCache()
    {
        ResourceSystem.println("Clearing cache");
        for (int i = 0; i < MAX_CACHE_ENTRIES; ++i)
        {
            CACHE[i].getEntry().setInfo(null);
            CACHE[i] = null;
        }
        CACHE_INDEX = 0;
        System.gc();
    }

    public static void addCache(FileNode node)
    {
        if (CACHE_INDEX == MAX_CACHE_ENTRIES)
            clearCache();
        int index = CACHE_INDEX++;
        CACHE[index] = node;
    }

    public static boolean GUI_MODE = false;

    private static File workingDirectory;

    static
    {
        try
        {
            ResourceSystem.workingDirectory = Files.createTempDirectory("twd").toFile();
            ResourceSystem.workingDirectory.deleteOnExit();
        }
        catch (IOException ex)
        {
            System.out.println("An error occurred creating temp directory.");
        }
    }

    private static final ExecutorService databaseService = Executors.newSingleThreadExecutor();
    private static final ExecutorService resourceService = Executors.newSingleThreadExecutor();

    private static final ArrayList<FileData> databases = new ArrayList<>();
    private static final ArrayList<Fart> archives = new ArrayList<>();

    private static RTranslationTable LAMS;

    private static final ArrayList<FileNode> selected = new ArrayList<>();
    private static FileNode lastSelected;

    private static FileData selectedDatabase;
    private static DatabaseType databaseType = DatabaseType.NONE;

    private static boolean canExtractLastNode = false;

    public static void println(Object message)
    {
        if (message == null || LOG_LEVEL < ResourceLogLevel.INFO) return;
        System.out.println("[ResourceSystem] " + message);
    }

    public static void println(String channel, Object message)
    {
        if (message == null || LOG_LEVEL < ResourceLogLevel.INFO) return;
        System.out.println("[" + channel + "] " + message);
    }

    public static void reset()
    {
        ResourceSystem.databaseType = DatabaseType.NONE;
        ResourceSystem.selectedDatabase = null;

        ResourceSystem.selected.clear();
        ResourceSystem.lastSelected = null;

        ResourceSystem.LAMS = null;

        ResourceSystem.archives.clear();
        ResourceSystem.databases.clear();
    }

    public static boolean canExtract()
    {
        FileData database = ResourceSystem.selectedDatabase;
        if (database == null) return false;
        DatabaseType type = database.getType();
        return type.containsData() || (type.hasGUIDs() && database.getBase() != null) || archives.size() > 0;
    }

    public static FileEntry get(ResourceDescriptor descriptor)
    {
        if (descriptor == null) return null;
        if (descriptor.isGUID()) return ResourceSystem.get(descriptor.getGUID());
        if (descriptor.isHash()) return ResourceSystem.get(descriptor.getSHA1());
        return null;
    }

    public static FileEntry get(long guid)
    {
        return ResourceSystem.get(new GUID(guid));
    }

    public static FileEntry get(GUID guid)
    {
        if (ResourceSystem.getDatabases().size() == 0) return null;

        // Prefer current database, can be null if not in GUI mode
        FileData current = ResourceSystem.selectedDatabase;
        if (current != null && current.getType().hasGUIDs())
        {
            FileEntry entry = current.get(guid);
            if (entry != null)
                return entry;
        }

        for (FileData database : ResourceSystem.getDatabases())
        {
            if (database == current) continue;
            if (database.getType().hasGUIDs())
            {
                FileEntry entry = database.get(guid);
                if (entry != null)
                    return entry;
            }
        }

        return null;
    }

    public static FileEntry get(SHA1 sha1)
    {
        if (ResourceSystem.getDatabases().size() == 0) return null;

        // Prefer current database, can be null if not in GUI mode
        FileData current = ResourceSystem.selectedDatabase;
        if (current != null)
        {
            FileEntry entry = current.get(sha1);
            if (entry != null)
                return entry;
        }

        for (FileData database : ResourceSystem.getDatabases())
        {
            if (database == current) continue;
            FileEntry entry = database.get(sha1);
            if (entry != null)
                return entry;
        }

        return null;
    }

    public static byte[] extract(ResourceDescriptor descriptor)
    {
        if (descriptor == null) return null;
        if (descriptor.isHash())
            return ResourceSystem.extract(descriptor.getSHA1());
        else if (descriptor.isGUID())
            return ResourceSystem.extract(descriptor.getGUID());
        return null;
    }

    public static byte[] extract(FileEntry entry)
    {
        if (entry == null) return null;
        byte[] data = ResourceSystem.extract(entry.getSHA1());
        if (data != null) return data;
        return ResourceSystem.extractFromDisk(entry);
    }

    public static byte[] extract(SHA1 hash)
    {
        for (FileData database : ResourceSystem.getDatabases())
        {
            if (database.getType().containsData())
            {
                byte[] data = database.extract(hash);
                if (data != null) return data;
            }
        }

        for (Fart fart : ResourceSystem.getArchives())
        {
            byte[] data = fart.extract(hash);
            if (data != null) return data;
        }

        return null;
    }

    public static byte[] extract(long guid)
    {
        return ResourceSystem.extract(new GUID(guid));
    }

    public static byte[] extract(GUID guid)
    {
        return ResourceSystem.extract(ResourceSystem.get(guid));
    }

    private static byte[] extractFromDisk(FileEntry entry)
    {
        if (entry == null) return null;
        FileData source = entry.getSource();
        File base = source.getBase();
        if (entry.getSource() == source && base != null)
        {
            File file = new File(base, entry.getPath());
            if (!file.exists()) return null;
            return FileIO.read(file.getAbsolutePath());
        }
        return null;
    }

    public static boolean add(byte[] data)
    {
        return ResourceSystem.add(data, ResourceSystem.selectedDatabase);
    }

    public static boolean add(byte[] data, FileData database)
    {
        if (database.getType().containsData())
        {
            database.add(data);

            database.setHasChanges();
            if (TriggerWorkSpaceUpdate != null)
                TriggerWorkSpaceUpdate.run();

            return true;
        }

        if (GetSelectedCaches != null)
        {
            Fart[] archives = GetSelectedCaches.get();
            if (archives == null || archives.length == 0) return false;

            ResourceSystem.add(data, archives);

            return true;
        }

        return false;
    }

    public static void add(byte[] data, Fart[] archives)
    {
        for (Fart archive : archives)
            archive.add(data);
    }

    public static boolean replace(FileEntry entry, byte[] data)
    {
        if (entry == null) return false;

        boolean code = true;
        if (ResourceSystem.getSelectedDatabase().getType() != DatabaseType.BIGFART)
            code = ResourceSystem.add(data, entry.getSource());

        if (code)
        {
            entry.setDetails(data);
            entry.setInfo(null);

            entry.getSource().setHasChanges();
            if (TriggerWorkSpaceUpdate != null)
                TriggerWorkSpaceUpdate.run();


            if (entry.getNode() == ResourceSystem.lastSelected)
                ResourceSystem.refreshEditor();
        }

        return code;
    }

    public static <T extends Serializable> T load(ResourceDescriptor descriptor, Class<T> clazz)
    {
        byte[] resourceData = extract(descriptor);
        if (resourceData == null) return null;
        return new SerializedResource(resourceData).loadResource(clazz);

    }

    public static void refreshEditor()
    {
        if (TreeSelectionListener != null)
            TreeSelectionListener.accept(ResourceSystem.getSelectedDatabase().getTree());
    }

    public static ArrayList<FileData> getDatabases()
    {
        return ResourceSystem.databases;
    }

    public static ArrayList<Fart> getArchives()
    {
        return ResourceSystem.archives;
    }

    public static RTranslationTable getLAMS()
    {
        return ResourceSystem.LAMS;
    }

    public static void setLAMS(RTranslationTable table)
    {
        ResourceSystem.LAMS = table;
    }

    public static FileNode getSelected()
    {
        return ResourceSystem.lastSelected;
    }

    public static FileNode[] getAllSelected()
    {
        return ResourceSystem.selected.toArray(FileNode[]::new);
    }

    public static DatabaseType getDatabaseType()
    {
        return ResourceSystem.databaseType;
    }

    public static int getLoadedDatabase(File file)
    {
        if (file == null) return -1;
        for (int i = 0; i < ResourceSystem.databases.size(); ++i)
        {
            FileData database = ResourceSystem.databases.get(i);
            if (database.getFile().equals(file))
                return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Resource> T getSelectedResource()
    {
        FileNode node = ResourceSystem.getSelected();
        if (node == null) return null;
        FileEntry entry = node.getEntry();
        if (entry == null) return null;
        ResourceInfo info = entry.getInfo();
        if (info == null) return null;
        return info.getResource();
    }

    public static void reloadModel(FileData database)
    {
        if (database == null) return;
        JTree tree = database.getTree();
        TreePath selectionPath = tree.getSelectionPath();
        ((FileModel) tree.getModel()).reload();
        tree.setSelectionPath(selectionPath);
        tree.scrollPathToVisible(selectionPath);
    }

    public static void reloadSelectedModel()
    {
        ResourceSystem.reloadModel(ResourceSystem.getSelectedDatabase());
    }

    @SuppressWarnings("unchecked")
    public static <T extends FileData> T getSelectedDatabase()
    {
        return (T) ResourceSystem.selectedDatabase;
    }

    public static FileData setSelectedDatabase(int index)
    {
        if (index < 0 || index >= ResourceSystem.databases.size())
        {
            ResourceSystem.databaseType = DatabaseType.NONE;
            ResourceSystem.selectedDatabase = null;
            return null;
        }
        FileData database = ResourceSystem.databases.get(index);
        ResourceSystem.selectedDatabase = database;
        ResourceSystem.databaseType = database.getType();
        return database;
    }

    public static File getWorkingDirectory()
    {
        return ResourceSystem.workingDirectory;
    }

    public static ExecutorService getDatabaseService()
    {
        return ResourceSystem.databaseService;
    }

    public static ExecutorService getResourceService()
    {
        return ResourceSystem.resourceService;
    }

    public static boolean canExtractSelected()
    {
        return ResourceSystem.canExtractLastNode;
    }

    public static void setCanExtractSelected(boolean value)
    {
        ResourceSystem.canExtractLastNode = value;
    }

    public static void resetSelections()
    {
        ResourceSystem.lastSelected = null;
        ResourceSystem.selected.clear();
    }

    public static FileNode updateSelections()
    {
        FileData data = ResourceSystem.getSelectedDatabase();
        return ResourceSystem.updateSelections((data == null) ? null : data.getTree());
    }

    public static FileNode updateSelections(JTree tree)
    {
        ResourceSystem.resetSelections();
        if (tree == null) return null;
        int[] rows = tree.getSelectionRows();
        if (rows == null) return null;

        FileNode selected = null;
        for (int row  : rows)
        {
            FileNode node = (FileNode) tree.getPathForRow(row).getLastPathComponent();
            if (node == null) continue;

            if (node.getChildCount() > 0)
                Nodes.loadChildren(ResourceSystem.selected, node, true);
            
            ResourceSystem.selected.add(node);
            selected = node;
        }

        ResourceSystem.lastSelected = selected;
        return selected;
    }
}
