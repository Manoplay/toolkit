package toolkit.windows;

import toolkit.windows.utilities.Compressinator;
import toolkit.windows.utilities.ArchiveSelector;
import toolkit.windows.utilities.AssetExporter;
import toolkit.windows.utilities.Dependinator;
import toolkit.windows.managers.ModManager;
import toolkit.windows.managers.ProfileManager;
import toolkit.windows.managers.ArchiveManager;
import toolkit.windows.managers.ItemManager;
import toolkit.windows.managers.SlotManager;
import cwlib.types.swing.FileNode;
import cwlib.types.swing.FileData;
import cwlib.types.swing.FileModel;
import cwlib.util.Nodes;
import cwlib.util.Strings;
import cwlib.types.swing.SearchParameters;
import cwlib.types.archives.Fart;
import cwlib.types.archives.SaveArchive;
import cwlib.ex.SerializationException;
import cwlib.resources.*;
import cwlib.util.FileIO;
import cwlib.resources.RPlan;
import cwlib.singleton.ResourceSystem;
import cwlib.enums.DatabaseType;
import cwlib.enums.InventoryObjectSubType;
import cwlib.enums.InventoryObjectType;
import cwlib.enums.ResourceType;
import cwlib.types.databases.FileDBRow;
import cwlib.types.databases.FileEntry;
import cwlib.structs.slot.Slot;
import cwlib.structs.inventory.InventoryItemDetails;

import java.awt.Color;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.PrintStream;
import java.sql.Timestamp;
import javax.swing.*;
import tv.porst.jhexview.JHexView;
import tv.porst.jhexview.SimpleDataProvider;
import cwlib.types.data.GUID;
import cwlib.types.data.ResourceDescriptor;
import cwlib.types.data.ResourceInfo;
import cwlib.types.mods.Mod;
import cwlib.types.save.BigSave;
import cwlib.types.save.SaveEntry;
import cwlib.util.Bytes;
import cwlib.util.Crypto;
import cwlib.util.Images;
import toolkit.configurations.Config;
import toolkit.configurations.Profile;
import toolkit.functions.*;
import toolkit.streams.CustomPrintStream;
import toolkit.streams.TextAreaOutputStream;
import toolkit.utilities.*;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.regex.Pattern;
import javax.swing.tree.TreePath;

public class Toolkit extends javax.swing.JFrame {
    public static Toolkit instance;

    public boolean useContext = false;

    MouseListener showContextMenu = new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                JTree tree = (JTree) e.getComponent();
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if (selPath != null) {
                    useContext = true;
                    tree.setSelectionPath(selPath);
                } else {
                    useContext = false;
                    tree.setSelectionPath(null);
                    ResourceSystem.resetSelections();
                }
                if (selRow > -1) {
                    tree.setSelectionRow(selRow);
                    useContext = true;
                } else useContext = false;
                ResourceSystem.updateSelections(tree);
                generateEntryContext(tree, e.getX(), e.getY());
            }
        }
    };

    public Toolkit() {
        /* Reset the state in case of a reboot. */
        ResourceSystem.reset();
        Toolkit.instance = this;
        
        this.initComponents();
        this.setIconImage(new ImageIcon(getClass().getResource("/legacy_icon.png")).getImage());
        
        EasterEgg.initialize(this);
        
        this.entryTable.getActionMap().put("copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                String copied = "";
                for (int i = 0; i < entryTable.getSelectedRowCount(); ++i) {
                    copied += String.valueOf(entryTable.getModel().getValueAt(entryTable.getSelectedRows()[i], 1));
                    if (i + 1 != entryTable.getSelectedRowCount()) copied += '\n';
                }
                StringSelection selection = new StringSelection(copied);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            }

        });
        
        this.progressBar.setVisible(false);
        this.fileDataTabs.addChangeListener(l -> {
            FileData database = ResourceSystem.setSelectedDatabase(this.fileDataTabs.getSelectedIndex());
            if (database == null) {
                this.search.setEnabled(false);
                this.search.setForeground(Color.GRAY);
                this.search.setText("Search is currently disabled.");
                
                this.updateWorkspace();
                
                return;
            }
            
            this.search.setEnabled(true);
            this.search.setText(database.getLastSearch());
            
            if (this.search.getText().equals("Search...")) search.setForeground(Color.GRAY);
            else this.search.setForeground(Color.WHITE);

            
            this.updateWorkspace();
        });
        
        /* Disable tabs since nothing is selected yet */
        this.entryModifiers.setEnabledAt(1, false);
        this.StringMetadata.setEnabled(false);
        this.updateWorkspace();
        
        
        this.search.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) {
                if (search.getText().equals("Search...")) {
                    search.setText("");
                    search.setForeground(Color.WHITE);
                }
            }
            
            public void focusLost(FocusEvent e) {
                if (search.getText().isEmpty()) {
                    search.setText("Search...");
                    search.setForeground(Color.GRAY);
                }
            }
        });

        this.dependencyTree.addMouseListener(showContextMenu);
        this.dependencyTree.addTreeSelectionListener(e -> TreeSelectionListener.listener(dependencyTree));

        /* Don't let the user close the program without
           confirming if they want to save or discard changes.
        */
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                checkForChanges();
            }
        });
        
        
        /* Auto-load configurations from whatever profile is currently enabled. */
        
        Profile profile = Config.instance.getCurrentProfile();
        if (profile == null) return;
        
        if (profile.archives != null) {
            for (String path : profile.archives) {
                if (Files.exists(Paths.get(path)))
                    ArchiveCallbacks.loadFileArchive(new File(path));
            }
        }
        
        if (profile.databases != null) {
            for (String path : profile.databases) {
                if (Files.exists(Paths.get(path)))
                    DatabaseCallbacks.loadFileDB(new File(path));
            }
        }        
    }

    private void checkForChanges() {
        for (FileData data: ResourceSystem.getDatabases()) {
            if (data.hasChanges()) {
                int result = JOptionPane.showConfirmDialog(null, String.format("Your %s (%s) has pending changes, do you want to save?", data.getType().getName(), data.getFile().getAbsolutePath()), "Pending changes", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) data.save();
            }
        }

        for (Fart archive: ResourceSystem.getArchives()) {
            if (archive.shouldSave()) {
                int result = JOptionPane.showConfirmDialog(null, String.format("Your FileArchive (%s) has pending changes, do you want to save?", archive.getFile().getAbsolutePath()), "Pending changes", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) archive.save();
            }
        }
    }

    public void updateWorkspace() {
        closeTab.setVisible(fileDataTabs.getTabCount() != 0);
        installProfileMod.setVisible(false);
        int archiveCount = ResourceSystem.getArchives().size();
        FileData database = ResourceSystem.getSelectedDatabase();

        if (database != null) {
            editMenu.setVisible(true);
            if (database.hasChanges()) {
                fileDataTabs.setTitleAt(fileDataTabs.getSelectedIndex(), database.getName() + " *");
                saveMenu.setEnabled(true);
            } else {
                fileDataTabs.setTitleAt(fileDataTabs.getSelectedIndex(), database.getName());
                saveMenu.setEnabled(false);
            }
        } else editMenu.setVisible(false);

        if (archiveCount != 0 || database != null) {
            saveDivider.setVisible(true);
            saveMenu.setVisible(true);
        } else {
            saveDivider.setVisible(false);
            saveMenu.setVisible(false);
        }

        addFolder.setVisible(false);
        
        if (ResourceSystem.getDatabaseType() == DatabaseType.NONE && ResourceSystem.getArchives().size() != 0) {
            FARMenu.setVisible(true);
            addFolder.setVisible(true);
        }
        else if (ResourceSystem.canExtract() && ResourceSystem.getDatabaseType() != DatabaseType.MOD) {
            FARMenu.setVisible(true); 
            addFolder.setVisible(ResourceSystem.getDatabaseType() == DatabaseType.FILE_DATABASE);
        }
        else FARMenu.setVisible(false);

        if (ResourceSystem.getDatabaseType() != DatabaseType.NONE) {
            saveDivider.setVisible(true);
            saveAs.setVisible(true);
        } else {
            saveDivider.setVisible(false);
            saveAs.setVisible(false);
        }

        if (database == null) {
            saveDivider.setVisible(false);
            dumpHashes.setVisible(false);
            MAPMenu.setVisible(false);
        } else {
            saveDivider.setVisible(true);
            dumpHashes.setVisible(true);
            dumpRLST.setVisible(false);
            if (ResourceSystem.getDatabaseType() == DatabaseType.FILE_DATABASE) {
                if (archiveCount != 0)
                    installProfileMod.setVisible(true);
                MAPMenu.setVisible(true);
                dumpHashes.setVisible(true);
                dumpRLST.setVisible(true);
            }
        }

        if (ResourceSystem.getDatabaseType() == DatabaseType.BIGFART) {
            ProfileMenu.setVisible(true);
            installProfileMod.setVisible(true);
        } else ProfileMenu.setVisible(false);

        modMenu.setVisible(ResourceSystem.getDatabaseType() == DatabaseType.MOD);
    }

    private void generateEntryContext(JTree tree, int x, int y) {
        exportTextureGroupContext.setVisible(false);
        editSlotContext.setVisible(false);
        exportLAMSContext.setVisible(false);
        loadLAMSContext.setVisible(false);
        replaceContext.setVisible(false);
        newFolderContext.setVisible(false);
        deleteContext.setVisible(false);
        zeroContext.setVisible(false);
        duplicateContext.setVisible(false);
        extractContextMenu.setVisible(false);
        newItemContext.setVisible(false);
        renameFolder.setVisible(false);
        replaceDecompressed.setVisible(false);
        replaceDependencies.setVisible(false);
        dependencyGroup.setVisible(false);
        exportModGroup.setVisible(false);
        exportBackupGroup.setVisible(false);
        exportAsBackupGUID.setVisible(false);
        exportAnimation.setVisible(false);
        exportModelGroup.setVisible(false);
        exportGroup.setVisible(false);
        replaceImage.setVisible(false);
        editMenuContext.setVisible(false);
        editItemContext.setVisible(false);
        
        boolean isDependencyTree = tree == this.dependencyTree;
        
        if (!useContext && isDependencyTree) return;

        if (!isDependencyTree || (ResourceSystem.getDatabaseType() == DatabaseType.BIGFART && useContext)) 
            deleteContext.setVisible(true);

        FileNode node = ResourceSystem.getSelected();
        FileEntry entry = node == null ? null : node.getEntry();
        ResourceInfo info = entry == null ? null : entry.getInfo();
        ResourceType type = info == null ? ResourceType.INVALID : info.getType();

        if (!(ResourceSystem.getDatabaseType() == DatabaseType.BIGFART) && ResourceSystem.getDatabases().size() != 0) {
            if ((useContext && entry == null) && !isDependencyTree) {
                newItemContext.setVisible(true);
                newFolderContext.setVisible(true);
                renameFolder.setVisible(true);
            } else if (!useContext) newFolderContext.setVisible(true);
            if (useContext) {
                if (!isDependencyTree) {
                    zeroContext.setVisible(true);
                    deleteContext.setVisible(true);
                }
                if (entry != null) {
                    duplicateContext.setVisible(true);
                    if (ResourceSystem.getDatabaseType() == DatabaseType.FILE_DATABASE && !isDependencyTree)
                        editMenuContext.setVisible(true);
                }
            }
        }

        if (ResourceSystem.canExtract() && node != null && entry != null) {
            replaceContext.setVisible(true);
            if (node.getName().endsWith(".tex") || (info != null && info.getType() == ResourceType.TEXTURE))
                replaceImage.setVisible(true);
        }

        if (ResourceSystem.canExtract() && ResourceSystem.canExtractSelected() && useContext) {
            extractContextMenu.setVisible(true);
            if (info != null && info.isResource() && type != ResourceType.FONTFACE && type != ResourceType.TRANSLATION) {
                if ((ResourceSystem.getDatabaseType() == DatabaseType.BIGFART || ResourceSystem.getDatabases().size() != 0) && info.isCompressedResource()) {
                    replaceDecompressed.setVisible(true);
                    if (info.getDependencies().length != 0) {
                        exportGroup.setVisible(true);
                        exportModGroup.setVisible(true);
                        if (info.getType() == ResourceType.PLAN || info.getType() == ResourceType.LEVEL) {
                            exportBackupGroup.setVisible(true);
                            exportAsBackupGUID.setVisible(entry.getKey() != null);
                        }
                        replaceDependencies.setVisible(true);
                        dependencyGroup.setVisible(true);
                    }
                }

                if (type == ResourceType.STATIC_MESH) replaceDecompressed.setVisible(false);
                if (info.getResource() != null) {
                    if (type == ResourceType.ANIMATION) {
                        exportGroup.setVisible(true);
                        exportAnimation.setVisible(true);
                    }
                    
                    if (type == ResourceType.STATIC_MESH) {
                        replaceDecompressed.setVisible(false);
                        if (info.getResource() != null) {
                            exportGroup.setVisible(true);
                            exportModelGroup.setVisible(true);
                            exportOBJ.setVisible(false);
                        }
                    }
    
                    if (type == ResourceType.MESH) {
                        RMesh mesh = info.getResource();
                        exportGroup.setVisible(true);
                        exportModelGroup.setVisible(true);
                        int count = mesh.getAttributeCount();
                        if (count != 0) 
                            exportOBJ.setVisible(true);
                        exportOBJTEXCOORD0.setVisible((count > 0));
                        exportOBJTEXCOORD1.setVisible((count > 1));
                        exportOBJTEXCOORD2.setVisible((count > 2));
                    }

                    if ((type == ResourceType.TEXTURE || type == ResourceType.GTF_TEXTURE)) {
                        exportGroup.setVisible(true);
                        exportTextureGroupContext.setVisible(true);
                        if (!isDependencyTree)
                            replaceImage.setVisible(true);
                    }
                    
                    if (type == ResourceType.SLOT_LIST && !isDependencyTree) 
                        editSlotContext.setVisible(true);
                    
                    if (type == ResourceType.ADVENTURE_CREATE_PROFILE && !isDependencyTree)
                        editSlotContext.setVisible(true);
                    
                    if (type == ResourceType.PLAN && !isDependencyTree) 
                        editItemContext.setVisible(true);
                    
                    if (type == ResourceType.PACKS && !isDependencyTree) 
                        editSlotContext.setVisible(true);
                    
                    if (type == ResourceType.LEVEL && ResourceSystem.getDatabaseType() == DatabaseType.BIGFART && !isDependencyTree)
                        editSlotContext.setVisible(true); 
                }
            }

            if (node.getName().endsWith(".trans")) {
                exportGroup.setVisible(true);
                loadLAMSContext.setVisible(true);
                exportLAMSContext.setVisible(true);
            }
        }

        entryContext.show(tree, x, y);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        entryContext = new javax.swing.JPopupMenu();
        extractContextMenu = new javax.swing.JMenu();
        extractContext = new javax.swing.JMenuItem();
        extractDecompressedContext = new javax.swing.JMenuItem();
        editMenuContext = new javax.swing.JMenu();
        renameItemContext = new javax.swing.JMenuItem();
        changeHash = new javax.swing.JMenuItem();
        changeGUID = new javax.swing.JMenuItem();
        editItemContext = new javax.swing.JMenuItem();
        editSlotContext = new javax.swing.JMenuItem();
        loadLAMSContext = new javax.swing.JMenuItem();
        exportGroup = new javax.swing.JMenu();
        exportTextureGroupContext = new javax.swing.JMenu();
        exportPNG = new javax.swing.JMenuItem();
        exportDDS = new javax.swing.JMenuItem();
        exportModelGroup = new javax.swing.JMenu();
        exportOBJ = new javax.swing.JMenu();
        exportOBJTEXCOORD0 = new javax.swing.JMenuItem();
        exportOBJTEXCOORD1 = new javax.swing.JMenuItem();
        exportOBJTEXCOORD2 = new javax.swing.JMenuItem();
        exportGLTF = new javax.swing.JMenuItem();
        exportLAMSContext = new javax.swing.JMenuItem();
        exportModGroup = new javax.swing.JMenu();
        exportAsModCustom = new javax.swing.JMenuItem();
        exportAsMod = new javax.swing.JMenuItem();
        exportAsModGUID = new javax.swing.JMenuItem();
        exportAnimation = new javax.swing.JMenuItem();
        exportBackupGroup = new javax.swing.JMenu();
        exportAsBackup = new javax.swing.JMenuItem();
        exportAsBackupGUID = new javax.swing.JMenuItem();
        replaceContext = new javax.swing.JMenu();
        replaceCompressed = new javax.swing.JMenuItem();
        replaceDecompressed = new javax.swing.JMenuItem();
        replaceDependencies = new javax.swing.JMenuItem();
        replaceImage = new javax.swing.JMenuItem();
        dependencyGroup = new javax.swing.JMenu();
        removeDependencies = new javax.swing.JMenuItem();
        removeMissingDependencies = new javax.swing.JMenuItem();
        newItemContext = new javax.swing.JMenuItem();
        newFolderContext = new javax.swing.JMenuItem();
        renameFolder = new javax.swing.JMenuItem();
        duplicateContext = new javax.swing.JMenuItem();
        zeroContext = new javax.swing.JMenuItem();
        deleteContext = new javax.swing.JMenuItem();
        consolePopup = new javax.swing.JPopupMenu();
        clear = new javax.swing.JMenuItem();
        metadataButtonGroup = new javax.swing.ButtonGroup();
        workspaceDivider = new javax.swing.JSplitPane();
        treeContainer = new javax.swing.JSplitPane();
        search = new javax.swing.JTextField();
        fileDataTabs = new javax.swing.JTabbedPane();
        details = new javax.swing.JSplitPane();
        previewContainer = new javax.swing.JSplitPane();
        consoleContainer = new javax.swing.JScrollPane();
        console = new javax.swing.JTextArea();
        preview = new javax.swing.JSplitPane();
        texture = new javax.swing.JLabel();
        hex = new tv.porst.jhexview.JHexView();
        entryData = new javax.swing.JSplitPane();
        tableContainer = new javax.swing.JScrollPane();
        entryTable = new javax.swing.JTable();
        entryModifiers = new javax.swing.JTabbedPane();
        dependencyTreeContainer = new javax.swing.JScrollPane();
        dependencyTree = new javax.swing.JTree();
        itemMetadata = new javax.swing.JPanel();
        LAMSMetadata = new javax.swing.JRadioButton();
        StringMetadata = new javax.swing.JRadioButton();
        iconLabel = new javax.swing.JLabel();
        iconField = new javax.swing.JTextField();
        titleLabel = new javax.swing.JLabel();
        descriptionLabel = new javax.swing.JLabel();
        descriptionField = new javax.swing.JTextArea();
        titleField = new javax.swing.JTextField();
        locationLabel = new javax.swing.JLabel();
        locationField = new javax.swing.JTextField();
        categoryLabel = new javax.swing.JLabel();
        categoryField = new javax.swing.JTextField();
        pageCombo = new javax.swing.JComboBox(InventoryObjectType.values());
        creatorLabel = new javax.swing.JLabel();
        creatorField = new javax.swing.JTextField();
        subCombo = new javax.swing.JTextField();
        progressBar = new javax.swing.JProgressBar();
        toolkitMenu = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        menuFileMenu = new javax.swing.JMenu();
        newGamedataGroup = new javax.swing.JMenu();
        newFileDBGroup = new javax.swing.JMenu();
        newLegacyDB = new javax.swing.JMenuItem();
        newVitaDB = new javax.swing.JMenuItem();
        newModernDB = new javax.swing.JMenuItem();
        createFileArchive = new javax.swing.JMenuItem();
        newMod = new javax.swing.JMenuItem();
        loadGroupMenu = new javax.swing.JMenu();
        gamedataMenu = new javax.swing.JMenu();
        loadDB = new javax.swing.JMenuItem();
        loadArchive = new javax.swing.JMenuItem();
        savedataMenu = new javax.swing.JMenu();
        loadBigProfile = new javax.swing.JMenuItem();
        loadMod = new javax.swing.JMenuItem();
        jSeparator9 = new javax.swing.JPopupMenu.Separator();
        manageProfile = new javax.swing.JMenuItem();
        saveDivider = new javax.swing.JPopupMenu.Separator();
        saveAs = new javax.swing.JMenuItem();
        saveMenu = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        closeTab = new javax.swing.JMenuItem();
        reboot = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        editMenuDelete = new javax.swing.JMenuItem();
        FARMenu = new javax.swing.JMenu();
        manageArchives = new javax.swing.JMenuItem();
        jSeparator10 = new javax.swing.JPopupMenu.Separator();
        addFile = new javax.swing.JMenuItem();
        addFolder = new javax.swing.JMenuItem();
        MAPMenu = new javax.swing.JMenu();
        patchMAP = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        dumpRLST = new javax.swing.JMenuItem();
        dumpHashes = new javax.swing.JMenuItem();
        ProfileMenu = new javax.swing.JMenu();
        extractBigProfile = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        editProfileSlots = new javax.swing.JMenuItem();
        editProfileItems = new javax.swing.JMenuItem();
        modMenu = new javax.swing.JMenu();
        openModMetadata = new javax.swing.JMenuItem();
        toolsMenu = new javax.swing.JMenu();
        openCompressinator = new javax.swing.JMenuItem();
        decompressResource = new javax.swing.JMenuItem();
        dumpSep = new javax.swing.JPopupMenu.Separator();
        generateDiff = new javax.swing.JMenuItem();
        fileArchiveIntegrityCheck = new javax.swing.JMenuItem();
        collectionD = new javax.swing.JMenu();
        collectorPresets = new javax.swing.JMenu();
        collectAllLevelDependencies = new javax.swing.JMenuItem();
        collectAllItemDependencies = new javax.swing.JMenuItem();
        customCollector = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        mergeFARCs = new javax.swing.JMenuItem();
        installProfileMod = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JPopupMenu.Separator();
        convertTexture = new javax.swing.JMenuItem();
        jSeparator7 = new javax.swing.JPopupMenu.Separator();
        swapProfilePlatform = new javax.swing.JMenuItem();
        debugMenu = new javax.swing.JMenu();
        debugLoadProfileBackup = new javax.swing.JMenuItem();

        extractContextMenu.setText("Extract");

        extractContext.setText("Extract");
        extractContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractContextActionPerformed(evt);
            }
        });
        extractContextMenu.add(extractContext);

        extractDecompressedContext.setText("Decompress");
        extractDecompressedContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractDecompressedContextActionPerformed(evt);
            }
        });
        extractContextMenu.add(extractDecompressedContext);

        entryContext.add(extractContextMenu);

        editMenuContext.setText("Edit");

        renameItemContext.setText("Path");
        renameItemContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renameItemContextActionPerformed(evt);
            }
        });
        editMenuContext.add(renameItemContext);

        changeHash.setText("Hash");
        changeHash.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changeHashActionPerformed(evt);
            }
        });
        editMenuContext.add(changeHash);

        changeGUID.setText("GUID");
        changeGUID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changeGUIDActionPerformed(evt);
            }
        });
        editMenuContext.add(changeGUID);

        entryContext.add(editMenuContext);

        editItemContext.setText("Edit Item Details");
        editItemContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editItemContextActionPerformed(evt);
            }
        });
        entryContext.add(editItemContext);

        editSlotContext.setText("Edit Slot");
        editSlotContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editSlotContextActionPerformed(evt);
            }
        });
        entryContext.add(editSlotContext);

        loadLAMSContext.setText("Load LAMS");
        loadLAMSContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadLAMSContextActionPerformed(evt);
            }
        });
        entryContext.add(loadLAMSContext);

        exportGroup.setText("Export");

        exportTextureGroupContext.setText("Textures");

        exportPNG.setText("PNG");
        exportPNG.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportPNGActionPerformed(evt);
            }
        });
        exportTextureGroupContext.add(exportPNG);

        exportDDS.setText("DDS");
        exportDDS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportDDSActionPerformed(evt);
            }
        });
        exportTextureGroupContext.add(exportDDS);

        exportGroup.add(exportTextureGroupContext);

        exportModelGroup.setText("Model");

        exportOBJ.setText("Wavefront");

        exportOBJTEXCOORD0.setText("TEXCOORD0");
        exportOBJTEXCOORD0.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportOBJTEXCOORD0ActionPerformed(evt);
            }
        });
        exportOBJ.add(exportOBJTEXCOORD0);

        exportOBJTEXCOORD1.setText("TEXCOORD1");
        exportOBJTEXCOORD1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportOBJTEXCOORD1ActionPerformed(evt);
            }
        });
        exportOBJ.add(exportOBJTEXCOORD1);

        exportOBJTEXCOORD2.setText("TEXCOORD2");
        exportOBJTEXCOORD2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportOBJTEXCOORD2ActionPerformed(evt);
            }
        });
        exportOBJ.add(exportOBJTEXCOORD2);

        exportModelGroup.add(exportOBJ);

        exportGLTF.setText("glTF 2.0");
        exportGLTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportGLTFActionPerformed(evt);
            }
        });
        exportModelGroup.add(exportGLTF);

        exportGroup.add(exportModelGroup);

        exportLAMSContext.setText("Text Document");
        exportLAMSContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportLAMSContextActionPerformed(evt);
            }
        });
        exportGroup.add(exportLAMSContext);

        exportModGroup.setText("Mod");

        exportAsModCustom.setActionCommand("Custom");
        exportAsModCustom.setLabel("Custom");
        exportAsModCustom.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsModCustomActionPerformed(evt);
            }
        });
        exportModGroup.add(exportAsModCustom);

        exportAsMod.setText("Hash");
        exportAsMod.setToolTipText("");
        exportAsMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsModActionPerformed(evt);
            }
        });
        exportModGroup.add(exportAsMod);

        exportAsModGUID.setText("GUID");
        exportAsModGUID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsModGUIDActionPerformed(evt);
            }
        });
        exportModGroup.add(exportAsModGUID);

        exportGroup.add(exportModGroup);

        exportAnimation.setText("Animation");
        exportAnimation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAnimationActionPerformed(evt);
            }
        });
        exportGroup.add(exportAnimation);

        exportBackupGroup.setText("Backup");

        exportAsBackup.setText("Hash");
        exportAsBackup.setToolTipText("");
        exportAsBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsBackupActionPerformed(evt);
            }
        });
        exportBackupGroup.add(exportAsBackup);

        exportAsBackupGUID.setText("GUID");
        exportAsBackupGUID.setActionCommand("exportAsBackupGUID");
        exportAsBackupGUID.setName(""); // NOI18N
        exportAsBackupGUID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsBackupGUIDActionPerformed(evt);
            }
        });
        exportBackupGroup.add(exportAsBackupGUID);

        exportGroup.add(exportBackupGroup);

        entryContext.add(exportGroup);

        replaceContext.setText("Replace");

        replaceCompressed.setText("Replace");
        replaceCompressed.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replaceCompressedActionPerformed(evt);
            }
        });
        replaceContext.add(replaceCompressed);

        replaceDecompressed.setText("Decompressed");
        replaceDecompressed.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replaceDecompressedActionPerformed(evt);
            }
        });
        replaceContext.add(replaceDecompressed);

        replaceDependencies.setText("Dependencies");
        replaceDependencies.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replaceDependenciesActionPerformed(evt);
            }
        });
        replaceContext.add(replaceDependencies);

        replaceImage.setText("Image");
        replaceImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replaceImageActionPerformed(evt);
            }
        });
        replaceContext.add(replaceImage);

        entryContext.add(replaceContext);

        dependencyGroup.setText("Dependencies");

        removeDependencies.setText("Remove Dependencies");
        removeDependencies.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeDependenciesActionPerformed(evt);
            }
        });
        dependencyGroup.add(removeDependencies);

        removeMissingDependencies.setText("Remove Missing Dependencies");
        removeMissingDependencies.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeMissingDependenciesActionPerformed(evt);
            }
        });
        dependencyGroup.add(removeMissingDependencies);

        entryContext.add(dependencyGroup);

        newItemContext.setText("New Item");
        newItemContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newItemContextActionPerformed(evt);
            }
        });
        entryContext.add(newItemContext);

        newFolderContext.setText("New Folder");
        newFolderContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newFolderContextActionPerformed(evt);
            }
        });
        entryContext.add(newFolderContext);

        renameFolder.setText("Rename Folder");
        renameFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renameFolderActionPerformed(evt);
            }
        });
        entryContext.add(renameFolder);

        duplicateContext.setText("Duplicate");
        duplicateContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                duplicateContextActionPerformed(evt);
            }
        });
        entryContext.add(duplicateContext);

        zeroContext.setText("Zero");
        zeroContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zeroContextActionPerformed(evt);
            }
        });
        entryContext.add(zeroContext);

        deleteContext.setText("Delete");
        deleteContext.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteContextActionPerformed(evt);
            }
        });
        entryContext.add(deleteContext);

        clear.setText("Clear");
        clear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearActionPerformed(evt);
            }
        });
        consolePopup.add(clear);

        metadataButtonGroup.add(LAMSMetadata);
        metadataButtonGroup.add(StringMetadata);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Craftworld Toolkit");
        setMinimumSize(new java.awt.Dimension(698, 296));

        workspaceDivider.setDividerLocation(275);

        treeContainer.setDividerLocation(30);
        treeContainer.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        treeContainer.setMinimumSize(new java.awt.Dimension(150, 200));
        treeContainer.setPreferredSize(new java.awt.Dimension(150, 200));

        search.setEditable(false);
        search.setText(" Search is currently disabled.");
        search.setBorder(null);
        search.setFocusable(false);
        search.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchActionPerformed(evt);
            }
        });
        treeContainer.setLeftComponent(search);
        treeContainer.setRightComponent(fileDataTabs);

        workspaceDivider.setLeftComponent(treeContainer);

        details.setResizeWeight(1);
        details.setDividerLocation(850);

        previewContainer.setDividerLocation(325);
        previewContainer.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        console.setEditable(false);
        console.setColumns(20);
        console.setLineWrap(true);
        console.setRows(5);
        PrintStream out = new CustomPrintStream(new TextAreaOutputStream(console));
        System.setOut(out);
        System.setErr(out);
        console.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                consoleMouseReleased(evt);
            }
        });
        consoleContainer.setViewportView(console);

        previewContainer.setBottomComponent(consoleContainer);

        preview.setDividerLocation(325);

        texture.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        texture.setText("No preview to be displayed.");
        texture.setToolTipText("");
        texture.setFocusable(false);
        preview.setLeftComponent(texture);

        javax.swing.GroupLayout hexLayout = new javax.swing.GroupLayout(hex);
        hex.setLayout(hexLayout);
        hexLayout.setHorizontalGroup(
            hexLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        hexLayout.setVerticalGroup(
            hexLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 322, Short.MAX_VALUE)
        );

        preview.setRightComponent(hex);

        previewContainer.setLeftComponent(preview);

        details.setLeftComponent(previewContainer);

        entryData.setDividerLocation(204);
        entryData.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        entryData.setMaximumSize(new java.awt.Dimension(55, 2147483647));
        entryData.setMinimumSize(new java.awt.Dimension(55, 102));
        entryData.setPreferredSize(new java.awt.Dimension(55, 1120));

        tableContainer.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        tableContainer.setMaximumSize(new java.awt.Dimension(452, 32767));
        tableContainer.setMinimumSize(new java.awt.Dimension(452, 6));

        entryTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {"Path", "N/A"},
                {"Timestamp", "N/A"},
                {"SHA1", "N/A"},
                {"Size", "N/A"},
                {"GUID", "N/A"},
                {"GUID (Hex)", "N/A"},
                {"GUID (7-bit)", "N/A"},
                {"Revision", "N/A"}
            },
            new String [] {
                "Field", "Value"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                true, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        entryTable.getTableHeader().setReorderingAllowed(false);
        tableContainer.setViewportView(entryTable);
        if (entryTable.getColumnModel().getColumnCount() > 0) {
            entryTable.getColumnModel().getColumn(0).setResizable(false);
            entryTable.getColumnModel().getColumn(1).setResizable(false);
        }

        entryData.setLeftComponent(tableContainer);

        entryModifiers.setMaximumSize(new java.awt.Dimension(452, 32767));
        entryModifiers.setMinimumSize(new java.awt.Dimension(452, 81));
        entryModifiers.setPreferredSize(new java.awt.Dimension(452, 713));

        dependencyTreeContainer.setAlignmentX(2.0F);

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        dependencyTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        dependencyTree.setRootVisible(false);
        dependencyTreeContainer.setViewportView(dependencyTree);

        entryModifiers.addTab("Dependencies", dependencyTreeContainer);

        LAMSMetadata.setText("LAMS");
        LAMSMetadata.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LAMSMetadataActionPerformed(evt);
            }
        });

        StringMetadata.setText("String");
        StringMetadata.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StringMetadataActionPerformed(evt);
            }
        });

        iconLabel.setText("Icon");

        iconField.setEditable(false);

        titleLabel.setText("Title");

        descriptionLabel.setText("Description");

        descriptionField.setEditable(false);
        descriptionField.setColumns(20);
        descriptionField.setLineWrap(true);
        descriptionField.setRows(5);
        descriptionField.setWrapStyleWord(true);

        titleField.setEditable(false);

        locationLabel.setText("Location");

        locationField.setEditable(false);
        locationField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                locationFieldActionPerformed(evt);
            }
        });

        categoryLabel.setText("Category");

        categoryField.setEditable(false);

        pageCombo.setEnabled(false);

        creatorLabel.setText("Creator");

        creatorField.setEditable(false);

        subCombo.setEditable(false);
        subCombo.setAutoscrolls(false);
        subCombo.setEnabled(false);

        javax.swing.GroupLayout itemMetadataLayout = new javax.swing.GroupLayout(itemMetadata);
        itemMetadata.setLayout(itemMetadataLayout);
        itemMetadataLayout.setHorizontalGroup(
            itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(itemMetadataLayout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(itemMetadataLayout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(LAMSMetadata, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(StringMetadata, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(descriptionLabel)
                    .addGroup(itemMetadataLayout.createSequentialGroup()
                        .addComponent(iconLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(iconField, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(itemMetadataLayout.createSequentialGroup()
                        .addComponent(titleLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(titleField, javax.swing.GroupLayout.PREFERRED_SIZE, 166, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(descriptionField, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(itemMetadataLayout.createSequentialGroup()
                        .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(locationLabel)
                            .addComponent(categoryLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(locationField)
                            .addComponent(categoryField)))
                    .addComponent(pageCombo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(itemMetadataLayout.createSequentialGroup()
                        .addComponent(creatorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(creatorField, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(subCombo))
                .addContainerGap(38, Short.MAX_VALUE))
        );
        itemMetadataLayout.setVerticalGroup(
            itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(itemMetadataLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(iconLabel)
                    .addComponent(iconField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(StringMetadata)
                    .addComponent(LAMSMetadata))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(titleLabel)
                    .addComponent(titleField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descriptionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descriptionField, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locationLabel)
                    .addComponent(locationField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(categoryLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(categoryField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pageCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(subCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(itemMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(creatorLabel)
                    .addComponent(creatorField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(38, Short.MAX_VALUE))
        );

        entryModifiers.addTab("Metadata", itemMetadata);

        entryData.setRightComponent(entryModifiers);

        details.setRightComponent(entryData);

        workspaceDivider.setRightComponent(details);

        progressBar.setEnabled(false);

        fileMenu.setText("File");

        menuFileMenu.setText("New");

        newGamedataGroup.setText("Gamedata");

        newFileDBGroup.setText("FileDB");

        newLegacyDB.setText("LBP1/2");
        newLegacyDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newLegacyDBActionPerformed(evt);
            }
        });
        newFileDBGroup.add(newLegacyDB);

        newVitaDB.setText("LBP Vita");
        newVitaDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newVitaDBActionPerformed(evt);
            }
        });
        newFileDBGroup.add(newVitaDB);

        newModernDB.setText("LBP3");
        newModernDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newModernDBActionPerformed(evt);
            }
        });
        newFileDBGroup.add(newModernDB);

        newGamedataGroup.add(newFileDBGroup);

        createFileArchive.setText("File Archive");
        createFileArchive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createFileArchiveActionPerformed(evt);
            }
        });
        newGamedataGroup.add(createFileArchive);

        menuFileMenu.add(newGamedataGroup);

        newMod.setText("Mod");
        newMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newModActionPerformed(evt);
            }
        });
        menuFileMenu.add(newMod);

        fileMenu.add(menuFileMenu);

        loadGroupMenu.setText("Load");

        gamedataMenu.setText("Gamedata");

        loadDB.setText("FileDB");
        loadDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDBActionPerformed(evt);
            }
        });
        gamedataMenu.add(loadDB);

        loadArchive.setText("File Archive");
        loadArchive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadArchiveActionPerformed(evt);
            }
        });
        gamedataMenu.add(loadArchive);

        loadGroupMenu.add(gamedataMenu);

        savedataMenu.setText("Savedata");

        loadBigProfile.setText("Big Profile");
        loadBigProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadBigProfileActionPerformed(evt);
            }
        });
        savedataMenu.add(loadBigProfile);

        loadGroupMenu.add(savedataMenu);

        loadMod.setText("Mod");
        loadMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadModActionPerformed(evt);
            }
        });
        loadGroupMenu.add(loadMod);

        fileMenu.add(loadGroupMenu);
        fileMenu.add(jSeparator9);

        manageProfile.setText("Manage Profiles");
        manageProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageProfileActionPerformed(evt);
            }
        });
        fileMenu.add(manageProfile);
        fileMenu.add(saveDivider);

        saveAs.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveAs.setText("Save as...");
        saveAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsActionPerformed(evt);
            }
        });
        fileMenu.add(saveAs);

        saveMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveMenu.setText("Save");
        saveMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMenuActionPerformed(evt);
            }
        });
        fileMenu.add(saveMenu);
        fileMenu.add(jSeparator4);

        closeTab.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        closeTab.setText("Close Tab");
        closeTab.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeTabActionPerformed(evt);
            }
        });
        fileMenu.add(closeTab);

        reboot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        reboot.setText("Reboot");
        reboot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebootActionPerformed(evt);
            }
        });
        fileMenu.add(reboot);

        toolkitMenu.add(fileMenu);

        editMenu.setText("Edit");

        editMenuDelete.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0));
        editMenuDelete.setText("Delete");
        editMenuDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editMenuDeleteActionPerformed(evt);
            }
        });
        editMenu.add(editMenuDelete);

        toolkitMenu.add(editMenu);

        FARMenu.setText("Archive");

        manageArchives.setText("Manage Archives");
        manageArchives.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageArchivesActionPerformed(evt);
            }
        });
        FARMenu.add(manageArchives);
        FARMenu.add(jSeparator10);

        addFile.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        addFile.setText("Add...");
        addFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addFileActionPerformed(evt);
            }
        });
        FARMenu.add(addFile);

        addFolder.setText("Add Folder");
        addFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addFolderActionPerformed(evt);
            }
        });
        FARMenu.add(addFolder);

        toolkitMenu.add(FARMenu);

        MAPMenu.setText("FileDB");

        patchMAP.setText("Patch");
        patchMAP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                patchMAPActionPerformed(evt);
            }
        });
        MAPMenu.add(patchMAP);
        MAPMenu.add(jSeparator6);

        dumpRLST.setText("Dump RLST");
        dumpRLST.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dumpRLSTActionPerformed(evt);
            }
        });
        MAPMenu.add(dumpRLST);

        dumpHashes.setText("Dump Hashes");
        dumpHashes.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dumpHashesActionPerformed(evt);
            }
        });
        MAPMenu.add(dumpHashes);

        toolkitMenu.add(MAPMenu);

        ProfileMenu.setText("Profile");

        extractBigProfile.setText("Extract Profile");
        extractBigProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractBigProfileActionPerformed(evt);
            }
        });
        ProfileMenu.add(extractBigProfile);
        ProfileMenu.add(jSeparator1);

        editProfileSlots.setText("Edit Slots");
        editProfileSlots.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editProfileSlotsActionPerformed(evt);
            }
        });
        ProfileMenu.add(editProfileSlots);

        editProfileItems.setText("Edit Items");
        editProfileItems.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editProfileItemsActionPerformed(evt);
            }
        });
        ProfileMenu.add(editProfileItems);

        toolkitMenu.add(ProfileMenu);

        modMenu.setText("Mod");

        openModMetadata.setText("Edit");
        openModMetadata.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openModMetadataActionPerformed(evt);
            }
        });
        modMenu.add(openModMetadata);

        toolkitMenu.add(modMenu);

        toolsMenu.setText("Tools");

        openCompressinator.setText("Compress");
        openCompressinator.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openCompressinatorActionPerformed(evt);
            }
        });
        toolsMenu.add(openCompressinator);

        decompressResource.setText("Decompress");
        decompressResource.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decompressResourceActionPerformed(evt);
            }
        });
        toolsMenu.add(decompressResource);
        toolsMenu.add(dumpSep);

        generateDiff.setText("Generate FileDB diff");
        generateDiff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateDiffActionPerformed(evt);
            }
        });
        toolsMenu.add(generateDiff);

        fileArchiveIntegrityCheck.setText("File Archive Integrity Check");
        fileArchiveIntegrityCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileArchiveIntegrityCheckActionPerformed(evt);
            }
        });
        toolsMenu.add(fileArchiveIntegrityCheck);

        collectionD.setText("Collectors");

        collectorPresets.setText("Presets");

        collectAllLevelDependencies.setText("RLevel");
        collectAllLevelDependencies.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                collectAllLevelDependenciesActionPerformed(evt);
            }
        });
        collectorPresets.add(collectAllLevelDependencies);

        collectAllItemDependencies.setText("RPlan");
        collectAllItemDependencies.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                collectAllItemDependenciesActionPerformed(evt);
            }
        });
        collectorPresets.add(collectAllItemDependencies);

        collectionD.add(collectorPresets);

        customCollector.setText("Custom");
        customCollector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                customCollectorActionPerformed(evt);
            }
        });
        collectionD.add(customCollector);

        toolsMenu.add(collectionD);
        toolsMenu.add(jSeparator3);

        mergeFARCs.setText("Merge FARCs");
        mergeFARCs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mergeFARCsActionPerformed(evt);
            }
        });
        toolsMenu.add(mergeFARCs);

        installProfileMod.setText("Install Mod");
        installProfileMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                installProfileModActionPerformed(evt);
            }
        });
        toolsMenu.add(installProfileMod);
        toolsMenu.add(jSeparator8);

        convertTexture.setText("Convert Texture");
        convertTexture.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                convertTextureActionPerformed(evt);
            }
        });
        toolsMenu.add(convertTexture);
        toolsMenu.add(jSeparator7);

        swapProfilePlatform.setText("Swap Profile Platform");
        swapProfilePlatform.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swapProfilePlatformActionPerformed(evt);
            }
        });
        toolsMenu.add(swapProfilePlatform);

        toolkitMenu.add(toolsMenu);

        debugMenu.setText("Debug");

        debugLoadProfileBackup.setText("Load Profile Backup");
        debugLoadProfileBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                debugLoadProfileBackupActionPerformed(evt);
            }
        });
        debugMenu.add(debugLoadProfileBackup);

        toolkitMenu.add(debugMenu);

        setJMenuBar(toolkitMenu);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(progressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(workspaceDivider, javax.swing.GroupLayout.DEFAULT_SIZE, 1385, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(workspaceDivider, javax.swing.GroupLayout.DEFAULT_SIZE, 591, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 7, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void loadDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadDBActionPerformed
        File file = FileChooser.openFile("blurayguids.map", "map", false);
        if (file != null) DatabaseCallbacks.loadFileDB(file);
    }//GEN-LAST:event_loadDBActionPerformed

    public void addTab(FileData data) {
        ResourceSystem.getDatabases().add(data);
        JTree tree = data.getTree();

        tree.addTreeSelectionListener(e -> TreeSelectionListener.listener(tree));
        tree.addMouseListener(showContextMenu);

        JScrollPane panel = new JScrollPane();
        panel.setViewportView(tree);

        fileDataTabs.addTab(data.getName(), panel);

        search.setEditable(true);
        search.setFocusable(true);
        search.setText("Search...");
        search.setForeground(Color.GRAY);

        fileDataTabs.setSelectedIndex(fileDataTabs.getTabCount() - 1);
        ResourceSystem.reloadSelectedModel();
    }

    public int isDatabaseLoaded(File file) {
        for (int i = 0; i < ResourceSystem.getDatabases().size(); ++i) {
            FileData data = ResourceSystem.getDatabases().get(i);
            if (data.getFile().equals(file))
                return i;
        }
        return -1;
    }

    private void loadArchiveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadArchiveActionPerformed
        File[] files = FileChooser.openFiles("data.farc", "farc");
        if (files == null) return;
        for (File file: files)
            ArchiveCallbacks.loadFileArchive(file);
    }//GEN-LAST:event_loadArchiveActionPerformed

    public int isArchiveLoaded(File file) {
        String path = file.getAbsolutePath();
        for (int i = 0; i < ResourceSystem.getArchives().size(); ++i) {
            Fart archive = ResourceSystem.getArchives().get(i);
            if (archive.getFile().getAbsolutePath().equals(path))
                return i;
        }
        return -1;
    }

    private void openCompressinatorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openCompressinatorActionPerformed
        new Compressinator().setVisible(true);
    }//GEN-LAST:event_openCompressinatorActionPerformed

    private void decompressResourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decompressResourceActionPerformed
        UtilityCallbacks.decompressResource();
    }//GEN-LAST:event_decompressResourceActionPerformed

    private void saveMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMenuActionPerformed
        FileCallbacks.save();
    }//GEN-LAST:event_saveMenuActionPerformed

    private void addFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addFileActionPerformed
        ArchiveCallbacks.addFile();
    }//GEN-LAST:event_addFileActionPerformed


    private void clearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearActionPerformed
        console.selectAll();
        console.replaceSelection("");
    }//GEN-LAST:event_clearActionPerformed

    private void consoleMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_consoleMouseReleased
        if (evt.isPopupTrigger())
            consolePopup.show(console, evt.getX(), evt.getY());
    }//GEN-LAST:event_consoleMouseReleased

    private void extractContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractContextActionPerformed
        ArchiveCallbacks.extract(false);
    }//GEN-LAST:event_extractContextActionPerformed

    private void extractDecompressedContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractDecompressedContextActionPerformed
        ArchiveCallbacks.extract(true);
    }//GEN-LAST:event_extractDecompressedContextActionPerformed

    private void exportOBJTEXCOORD0ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOBJTEXCOORD0ActionPerformed
        ExportCallbacks.exportOBJ(0);
    }//GEN-LAST:event_exportOBJTEXCOORD0ActionPerformed

    private void loadLAMSContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadLAMSContextActionPerformed
        byte[] data = ResourceSystem.extract(ResourceSystem.getSelected().getEntry());
        if (data == null) return;
        RTranslationTable table = new RTranslationTable(data);
        if (table != null) StringMetadata.setEnabled(true);
        ResourceSystem.setLAMS(table);
    }//GEN-LAST:event_loadLAMSContextActionPerformed

    private void locationFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_locationFieldActionPerformed

    }//GEN-LAST:event_locationFieldActionPerformed

    private void LAMSMetadataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LAMSMetadataActionPerformed
        ResourceInfo info = ResourceSystem.getSelected().getEntry().getInfo();
        if (info == null || info.getType() != ResourceType.PLAN || info.getResource() == null) return;
        InventoryItemDetails details = info.<RPlan>getResource().inventoryData;

        titleField.setText("" + details.titleKey);
        descriptionField.setText("" + details.descriptionKey);
        locationField.setText("" + details.location);
        categoryField.setText("" + details.category);
    }//GEN-LAST:event_LAMSMetadataActionPerformed

    private void StringMetadataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_StringMetadataActionPerformed
        ResourceInfo info = ResourceSystem.getSelected().getEntry().getInfo();
        if (info == null || info.getType() != ResourceType.PLAN || info.getResource() == null) return;
        InventoryItemDetails details = info.<RPlan>getResource().inventoryData;

        titleField.setText("");
        categoryField.setText("");
        locationField.setText("");
        categoryField.setText("");
        
        RTranslationTable LAMS = ResourceSystem.getLAMS();
        if (LAMS != null) {
            details.translatedTitle = LAMS.translate(details.titleKey);
            details.translatedDescription = LAMS.translate(details.descriptionKey);
            details.translatedCategory = LAMS.translate(details.category);
            details.translatedLocation = LAMS.translate(details.location);
            
            titleField.setText(details.translatedTitle);
            descriptionField.setText(details.translatedDescription);
            locationField.setText(details.translatedLocation);
            categoryField.setText(details.translatedCategory);
        }
        
        if (details.userCreatedDetails != null) {
            titleField.setText(details.userCreatedDetails.name);
            descriptionField.setText(details.userCreatedDetails.description);
        }
    }//GEN-LAST:event_StringMetadataActionPerformed

    private void loadBigProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadBigProfileActionPerformed
        ProfileCallbacks.loadProfile();
    }//GEN-LAST:event_loadBigProfileActionPerformed

    public Fart[] getSelectedArchives() {
        if (ResourceSystem.getArchives().size() == 0) return null;
        if (ResourceSystem.getArchives().size() > 1) {
            Fart[] archives = new ArchiveSelector(this, true).getSelected();
            if (archives == null) System.out.println("User did not select any FileArchives, cancelling operation.");
            return archives;
        }
        return new Fart[] {
            ResourceSystem.getArchives().get(0)
        };
    }

    private void searchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchActionPerformed
        FileData database = ResourceSystem.getSelectedDatabase();
        database.setLastSearch(search.getText());
        JTree tree = database.getTree();
        Nodes.filter((FileNode) tree.getModel().getRoot(), new SearchParameters(search.getText()));
        ((FileModel) tree.getModel()).reload();
        tree.updateUI();
    }//GEN-LAST:event_searchActionPerformed

    private void dumpHashesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dumpHashesActionPerformed
        DatabaseCallbacks.dumpHashes();
    }//GEN-LAST:event_dumpHashesActionPerformed

    private void extractBigProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractBigProfileActionPerformed
        ProfileCallbacks.extractProfile();
    }//GEN-LAST:event_extractBigProfileActionPerformed

    private void editSlotContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editSlotContextActionPerformed
        FileEntry entry = ResourceSystem.getSelected().getEntry();
        ResourceInfo info = entry.getInfo();
        if (info == null  || info.getType() == ResourceType.INVALID || info.getResource() == null) return;


        if (info.getType() == ResourceType.ADVENTURE_CREATE_PROFILE) {
            new SlotManager(entry, (RAdventureCreateProfile) info.getResource()).setVisible(true);
            return;
        }
        
        if (ResourceSystem.getDatabaseType() == DatabaseType.BIGFART) {
            Slot slot = ((SaveEntry)entry).getSlot();
            if (slot == null) return;
            new SlotManager((BigSave)entry.getSource(), slot).setVisible(true);
            return;
        }

        if (info.getType() == ResourceType.SLOT_LIST) 
            new SlotManager(entry, (RSlotList) info.getResource()).setVisible(true);
        else if (info.getType() == ResourceType.PACKS)
            new SlotManager(entry, (RPacks) info.getResource()).setVisible(true);
    }//GEN-LAST:event_editSlotContextActionPerformed

    private void exportLAMSContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportLAMSContextActionPerformed
        ExportCallbacks.exportTranslations();
    }//GEN-LAST:event_exportLAMSContextActionPerformed

    private void exportDDSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportDDSActionPerformed
        ExportCallbacks.exportDDS();
    }//GEN-LAST:event_exportDDSActionPerformed

    private void exportPNGActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportPNGActionPerformed
        ExportCallbacks.exportTexture("png");
    }//GEN-LAST:event_exportPNGActionPerformed

    private void patchMAPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_patchMAPActionPerformed
        DatabaseCallbacks.patchDatabase();
    }//GEN-LAST:event_patchMAPActionPerformed

    private void replaceCompressedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replaceCompressedActionPerformed
        ReplacementCallbacks.replaceCompressed();
    }//GEN-LAST:event_replaceCompressedActionPerformed

    private void mergeFARCsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mergeFARCsActionPerformed
        UtilityCallbacks.mergeFileArchives();
    }//GEN-LAST:event_mergeFARCsActionPerformed

    private void saveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsActionPerformed
        FileCallbacks.saveAs();
    }//GEN-LAST:event_saveAsActionPerformed

    private void deleteContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteContextActionPerformed
        DatabaseCallbacks.delete();
    }//GEN-LAST:event_deleteContextActionPerformed

    private void zeroContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zeroContextActionPerformed
        DatabaseCallbacks.zero();
    }//GEN-LAST:event_zeroContextActionPerformed

    private void newFolderContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newFolderContextActionPerformed
        DatabaseCallbacks.newFolder();
    }//GEN-LAST:event_newFolderContextActionPerformed

    private void generateDiffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateDiffActionPerformed
        UtilityCallbacks.generateFileDBDiff();
    }//GEN-LAST:event_generateDiffActionPerformed

    private void loadModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadModActionPerformed
        File file = FileChooser.openFile("example.mod", "mod", false);
        if (file == null) return;
        Mod mod = ModCallbacks.loadMod(file);
        if (mod != null) {
            addTab(mod);
            updateWorkspace();
        }
    }//GEN-LAST:event_loadModActionPerformed

    private void openModMetadataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openModMetadataActionPerformed
        new ModManager(ResourceSystem.getSelectedDatabase(), false).setVisible(true);
    }//GEN-LAST:event_openModMetadataActionPerformed

    private void editProfileSlotsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editProfileSlotsActionPerformed
        new SlotManager((BigSave) ResourceSystem.getSelectedDatabase(), null).setVisible(true);
    }//GEN-LAST:event_editProfileSlotsActionPerformed

    private void newVitaDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newVitaDBActionPerformed
        DatabaseCallbacks.newFileDB(936);
    }//GEN-LAST:event_newVitaDBActionPerformed

    private void newModernDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newModernDBActionPerformed
        DatabaseCallbacks.newFileDB(21496064);
    }//GEN-LAST:event_newModernDBActionPerformed

    private void newLegacyDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newLegacyDBActionPerformed
        DatabaseCallbacks.newFileDB(256);
    }//GEN-LAST:event_newLegacyDBActionPerformed

    public boolean confirmOverwrite(File file) {
        if (file.exists()) {
            int result = JOptionPane.showConfirmDialog(null, "This file already exists, are you sure you want to override it?", "Confirm overwrite", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.NO_OPTION) return false;
            return true;
        }
        return true;
    }

    private void createFileArchiveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createFileArchiveActionPerformed
        ArchiveCallbacks.newFileArchive();
    }//GEN-LAST:event_createFileArchiveActionPerformed

    private void editProfileItemsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editProfileItemsActionPerformed
        new ItemManager((BigSave) ResourceSystem.getSelectedDatabase()).setVisible(true);
    }//GEN-LAST:event_editProfileItemsActionPerformed

    private void installProfileModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_installProfileModActionPerformed
        UtilityCallbacks.installMod();
    }//GEN-LAST:event_installProfileModActionPerformed

    private void duplicateContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_duplicateContextActionPerformed
        DatabaseCallbacks.duplicateItem();
    }//GEN-LAST:event_duplicateContextActionPerformed

    private void newItemContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newItemContextActionPerformed
        DatabaseCallbacks.newItem();
    }//GEN-LAST:event_newItemContextActionPerformed

    private void replaceDecompressedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replaceDecompressedActionPerformed
        ReplacementCallbacks.replaceDecompressed();
    }//GEN-LAST:event_replaceDecompressedActionPerformed

    private void replaceDependenciesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replaceDependenciesActionPerformed
        new Dependinator(this, ResourceSystem.getSelected().getEntry());
    }//GEN-LAST:event_replaceDependenciesActionPerformed

    private void exportAsModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAsModActionPerformed
        ExportCallbacks.exportMod(true);
    }//GEN-LAST:event_exportAsModActionPerformed

    private void dumpRLSTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dumpRLSTActionPerformed
        DatabaseCallbacks.dumpRLST();
    }//GEN-LAST:event_dumpRLSTActionPerformed

    private void removeDependenciesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeDependenciesActionPerformed
        DependencyCallbacks.removeDependencies();
    }//GEN-LAST:event_removeDependenciesActionPerformed

    private void removeMissingDependenciesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeMissingDependenciesActionPerformed
        DependencyCallbacks.removeMissingDependencies();
    }//GEN-LAST:event_removeMissingDependenciesActionPerformed

    private void exportOBJTEXCOORD1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOBJTEXCOORD1ActionPerformed
        ExportCallbacks.exportOBJ(1);
    }//GEN-LAST:event_exportOBJTEXCOORD1ActionPerformed

    private void exportOBJTEXCOORD2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportOBJTEXCOORD2ActionPerformed
        ExportCallbacks.exportOBJ(2);
    }//GEN-LAST:event_exportOBJTEXCOORD2ActionPerformed

    private void replaceImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replaceImageActionPerformed
        ReplacementCallbacks.replaceImage();
    }//GEN-LAST:event_replaceImageActionPerformed

    private void rebootActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebootActionPerformed
        this.search.getParent().remove(this.search);
        this.checkForChanges();
        this.dispose();
        EventQueue.invokeLater(() -> new Toolkit().setVisible(true));
    }//GEN-LAST:event_rebootActionPerformed

    private void closeTabActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeTabActionPerformed
        FileCallbacks.closeTab();
    }//GEN-LAST:event_closeTabActionPerformed

    private void exportGLTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportGLTFActionPerformed
        ExportCallbacks.exportGLB();
    }//GEN-LAST:event_exportGLTFActionPerformed

    private void newModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newModActionPerformed
        UtilityCallbacks.newMod();
    }//GEN-LAST:event_newModActionPerformed

    private void exportAsModGUIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAsModGUIDActionPerformed
        ExportCallbacks.exportMod(false);
    }//GEN-LAST:event_exportAsModGUIDActionPerformed

    private void renameItemContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renameItemContextActionPerformed
        DatabaseCallbacks.renameItem();
    }//GEN-LAST:event_renameItemContextActionPerformed

    private void changeGUIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeGUIDActionPerformed
        DatabaseCallbacks.changeGUID();
    }//GEN-LAST:event_changeGUIDActionPerformed

    private void changeHashActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeHashActionPerformed
        DatabaseCallbacks.changeHash();
    }//GEN-LAST:event_changeHashActionPerformed

    private void exportAnimationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAnimationActionPerformed
        ExportCallbacks.exportAnimation();
    }//GEN-LAST:event_exportAnimationActionPerformed

    private void swapProfilePlatformActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_swapProfilePlatformActionPerformed
        File FAR4 = FileChooser.openFile("bigfart", null, false);
        if (FAR4 == null) return;
        if (FAR4.exists()) {
            SaveArchive archive = null;
            try { archive = new SaveArchive(FAR4); }
            catch (SerializationException ex) {
                System.err.println(ex.getMessage());
                return;
            }
            if (archive.getArchiveRevision() != 4) {
                System.out.println("FileArchive isn't a FAR4!");
                return;
            }
            boolean wasPS4 = archive.isLittleEndian();
            archive.setLittleEndian(!wasPS4);

            FileIO.write(archive.build(), FAR4.getAbsolutePath());
            JOptionPane.showMessageDialog(this, 
                    String.format("FAR4 has been swapped to %s endianness.", 
                            (!wasPS4) ? "PS4" : "PS3"));
        } else 
            System.out.println(String.format("%s does not exist!", FAR4.getAbsolutePath()));
    }//GEN-LAST:event_swapProfilePlatformActionPerformed

    private void addFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addFolderActionPerformed
        ArchiveCallbacks.addFolder();
    }//GEN-LAST:event_addFolderActionPerformed

    private void fileArchiveIntegrityCheckActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileArchiveIntegrityCheckActionPerformed
        ArchiveCallbacks.integrityCheck();
    }//GEN-LAST:event_fileArchiveIntegrityCheckActionPerformed


    enum ExportMode {
        GUID,
        Hash
    }

    private void exportAsBackupActionPerformed(java.awt.event.ActionEvent evt) {
        exportAsBackupTpl(ExportMode.Hash);
    }
    
    private void exportAsBackupTpl(ExportMode mode) {
        // TODO: Actually reimplement this
        // FileEntry entry = ResourceSystem.getSelected().getEntry();
        // String name = Paths.get(ResourceSystem.getSelected().getEntry().path).getFileName().toString();

        // String titleID = JOptionPane.showInputDialog(Toolkit.instance, "TitleID", "BCUS98148");
        // if (titleID == null) return;
        
        // String directory = FileChooser.openDirectory();
        // if (directory == null) return;
        
        // Revision fartRevision = new Revision(0x272, 0x4c44, 0x0017);
        // FileArchive archive = new FileArchive();
        
        // Slot slot = new Slot();
        // slot.title = name;
        // slot.id = new SlotID(SlotType.FAKE, 0);
        // slot.icon = new ResourceDescriptor(10682, ResourceType.TEXTURE);
        
        // // NOTE(Aidan): Cheap trick, but it's what I'm doing for now.
        // Resource resource = null;
        // switch (mode) {
        //     case Hash:
        //         resource = new Resource(ResourceSystem.extract(entry.hash));
        //         break;
        //     case GUID:
        //         resource = new Resource(ResourceSystem.extract(entry.GUID));
        //         break;
        // }
        // Mod mod = new Mod();
        // SHA1 hash = Bytes.hashinate(mod, resource, entry, null);

        // archive.entries = mod.entries;
        
        // if (entry.path.endsWith(".bin"))
        //     switch (mode) {
        //         case Hash:
        //             slot.root = new ResourceDescriptor(hash, ResourceType.LEVEL);
        //             break;
        //         case GUID:
        //             slot.root = new ResourceDescriptor(entry.GUID, ResourceType.LEVEL);
        //             break;
        //     }
        // else if (entry.path.endsWith(".plan")) {
        //     Resource level = new Resource(FileIO.getResourceFile("/prize_template"));
        //     switch (mode) {
        //         case Hash:
        //             level.replaceDependency(level.dependencies.get(0xB), new ResourceDescriptor(hash, ResourceType.PLAN));
        //             break;
        //         case GUID:
        //             level.replaceDependency(level.dependencies.get(0xB), new ResourceDescriptor(entry.GUID, ResourceType.PLAN));
        //             break;
        //     }
        //     byte[] levelData = level.compressToResource();
        //     archive.add(levelData);
        //     slot.root = new ResourceDescriptor(SHA1.fromBuffer(levelData), ResourceType.LEVEL);
        // }
        
        // Serializer serializer = new Serializer(1024, fartRevision, (byte) 0x7);
        // serializer.array(new Slot[] { slot }, Slot.class);
        // byte[] slotList = Resource.compressToResource(serializer.output, ResourceType.SLOT_LIST);
        // archive.add(slotList);
        // SHA1 rootHash = SHA1.fromBuffer(slotList);
        
        // archive.setFatDataSource(rootHash);
        // archive.setFatResourceType(ResourceType.SLOT_LIST);
        // archive.setFatRevision(fartRevision);
        
        // Random random = new Random();
        // byte[] UID = new byte[4];
        // random.nextBytes(UID);
        // titleID += "LEVEL" + Bytes.toHex(UID).toUpperCase();
        
        // Path saveDirectory = Path.of(directory, titleID);
        // try { Files.createDirectories(saveDirectory); } 
        // catch (IOException ex) {
        //     System.err.println("There was an error creating directory!");
        //     return;
        // }
        
        // FileIO.write(new ParamSFO(titleID, name).build(), Path.of(saveDirectory.toString(), "PARAM.SFO").toString());
        // FileIO.write(FileIO.getResourceFile("/default.png"), Path.of(saveDirectory.toString(), "ICON0.PNG").toString());
        
        // // NOTE(Aidan): This seems terribly inefficient in terms of memory cost,
        // // but the levels exported should be low, so it's not entirely an issue,
        // // FOR NOW.
        
        // byte[][] profiles = null;
        // {
        //     byte[] profile = archive.build();
        //     profile = Arrays.copyOfRange(profile, 0, profile.length - 4);
        //     profiles = Bytes.split(profile, 0x240000);
        // }
        
        // for (int i = 0; i < profiles.length; ++i) {
        //     byte[] part = profiles[i];
        //     part = Crypto.XXTEA(part, false);
        //     if (i + 1 == profiles.length)
        //         part = Bytes.combine(part, new byte[] { 0x46, 0x41, 0x52, 0x34 });
        //     FileIO.write(part, (Path.of(saveDirectory.toString(), String.valueOf(i))).toString());
        // }
    }                                              

    private void convertTextureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_convertTextureActionPerformed

        File file = FileChooser.openFile("image.png", "png,jpg,jpeg,dds", false);
        if (file == null) return;
        
        File save = FileChooser.openFile("image.tex", "tex", true);
        if (save == null) return;
        
        BufferedImage image;
        if (file.getAbsolutePath().toLowerCase().endsWith(".dds"))
            image = Images.fromDDS(FileIO.read(file.getAbsolutePath()));
        else image = FileIO.readBufferedImage(file.getAbsolutePath());

        if (image == null) { System.err.println("Image was null!"); return; }
        
        byte[] texture = Images.toTEX(image);
        if (texture == null) { System.err.println("Conversion was null!"); return; }
        
        FileIO.write(texture, save.getAbsolutePath());        
    }//GEN-LAST:event_convertTextureActionPerformed

    private void collectAllItemDependenciesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collectAllItemDependenciesActionPerformed
        DebugCallbacks.CollectDependencies(".plan");
    }//GEN-LAST:event_collectAllItemDependenciesActionPerformed

    private void collectAllLevelDependenciesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collectAllLevelDependenciesActionPerformed
       DebugCallbacks.CollectDependencies(".bin");
    }//GEN-LAST:event_collectAllLevelDependenciesActionPerformed

    private void customCollectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customCollectorActionPerformed
        String extension = JOptionPane.showInputDialog(Toolkit.instance, "File extension", ".plan");
        if (extension == null) return;
        DebugCallbacks.CollectDependencies(extension);
    }//GEN-LAST:event_customCollectorActionPerformed

    private void renameFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renameFolderActionPerformed
        FileNode node = ResourceSystem.getSelected();
        FileNode[] selected = ResourceSystem.getAllSelected();
        String parent = node.getFilePath() + node.getName();
        
        String newFolder = JOptionPane.showInputDialog(Toolkit.instance, "Folder", parent);
        if (newFolder == null) return;
        newFolder = Strings.cleanupPath(newFolder);
        if (newFolder == parent) return;
        
        FileData database = node.getSource();
        
        for (FileNode child : selected) {
            if (child == node) continue;
            FileEntry entry = child.getEntry();
            if (entry != null)
                entry.setFolder(newFolder);
            else node.removeFromParent(); 
        }
        
        database.setHasChanges();

        // JTree tree = node.getSource().getTree();
        // TreePath treePath = new TreePath(((FileNode) lastNode.getParent()).getPath());
        
        FileModel m = (FileModel) node.getSource().getTree().getModel();
        m.reload((FileNode) m.getRoot());

        // tree.setSelectionPath(treePath);
        // tree.scrollPathToVisible(treePath);

        Toolkit.instance.updateWorkspace();
    }//GEN-LAST:event_renameFolderActionPerformed

    private void editMenuDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editMenuDeleteActionPerformed
        DatabaseCallbacks.delete();
    }//GEN-LAST:event_editMenuDeleteActionPerformed

    private void debugLoadProfileBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_debugLoadProfileBackupActionPerformed
        String directoryString = FileChooser.openDirectory();
        if (directoryString == null) return;
        File directory = new File(directoryString);
        Pattern regex = Pattern.compile("BIG\\d+");
        File[] fragments = directory.listFiles((dir, name) -> regex.matcher(name).matches());
        if (fragments.length == 0) {
            System.out.println("Couldn't find a profile backup in this directory!");
            return;
        }
        byte[][] data = new byte[fragments.length + 1][];
        data[fragments.length] = new byte[] { 0x46, 0x41, 0x52, 0x34 };
        for (int i = 0; i < fragments.length; ++i) {
            byte[] fragment = FileIO.read(fragments[i].getAbsolutePath());
            if (i + 1 == fragments.length) 
                fragment = Arrays.copyOfRange(fragment, 0, fragment.length - 4);
            data[i] = Crypto.XXTEA(fragment, true);
        }
        File save = new File(ResourceSystem.getWorkingDirectory(), directory.getName());
        save.deleteOnExit();
        FileIO.write(Bytes.combine(data), save.getAbsolutePath());
        ProfileCallbacks.loadProfile(save);
    }//GEN-LAST:event_debugLoadProfileBackupActionPerformed

    private void manageProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageProfileActionPerformed
        ProfileManager manager = new ProfileManager(this);
        manager.setVisible(true);
        Config.save();
    }//GEN-LAST:event_manageProfileActionPerformed

    private void manageArchivesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageArchivesActionPerformed
        ArchiveManager manager = new ArchiveManager(this);
        manager.setVisible(true);
    }//GEN-LAST:event_manageArchivesActionPerformed

    private void editItemContextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editItemContextActionPerformed
        FileEntry entry = ResourceSystem.getSelected().getEntry();
        RPlan plan = entry.getInfo().getResource();
        if (plan == null) return;
        ItemManager manager = new ItemManager(entry, plan);
        manager.setVisible(true);
    }//GEN-LAST:event_editItemContextActionPerformed

    private void exportAsBackupGUIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAsBackupGUIDActionPerformed
        exportAsBackupTpl(ExportMode.GUID);
    }//GEN-LAST:event_exportAsBackupGUIDActionPerformed

    private void exportAsModCustomActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAsModCustomActionPerformed
        new AssetExporter(ResourceSystem.getSelected().getEntry()).setVisible(true);
    }//GEN-LAST:event_exportAsModCustomActionPerformed

    public void generateDependencyTree(FileEntry entry, FileModel model) {
        // TODO: FIX DEPENDENCY TREE
        // if (entry.dependencies != null) {
        //     FileNode root = (FileNode) model.getRoot();
        //     for (int i = 0; i < entry.dependencies.size(); ++i) {
        //         FileEntry dependencyEntry = ResourceSystem.get(entry.dependencies.get(i));
        //         if (dependencyEntry == null || dependencyEntry.path == null) continue;
        //         Nodes.addNode(root, dependencyEntry);
        //         if (dependencyEntry.dependencies != null && dependencyEntry != entry)
        //             generateDependencyTree(dependencyEntry, model);
        //     }
        // }
    }

    public void populateMetadata(RPlan item) {
        if (item == null || !ResourceSystem.canExtract()) return;
        InventoryItemDetails metadata = item.inventoryData;
        if (metadata == null) return;

        iconField.setText("");
        if (metadata.icon != null)
            loadImage(metadata.icon, item);

        if (ResourceSystem.getSelected().getEntry().getInfo().getResource() != item) return;

        setPlanDescriptions(metadata);

        if (metadata.type.isEmpty())
            pageCombo.setSelectedItem(InventoryObjectType.NONE);
        else 
            pageCombo.setSelectedItem(metadata.type.iterator().next());
        subCombo.setText(InventoryObjectSubType.getTypeString(metadata.type, metadata.subType));
        
        if (metadata.creator != null)
            creatorField.setText(metadata.creator.toString());
        else creatorField.setText("");
        
        entryModifiers.setEnabledAt(1, true);
        entryModifiers.setSelectedIndex(1);
    }

    public void setPlanDescriptions(InventoryItemDetails metadata) {
        titleField.setText("" + metadata.titleKey);
        descriptionField.setText("" + metadata.descriptionKey);

        locationField.setText("" + metadata.location);
        categoryField.setText("" + metadata.category);

        RTranslationTable LAMS = ResourceSystem.getLAMS();
        if (LAMS != null) {
            StringMetadata.setEnabled(true);
            StringMetadata.setSelected(true);
            
            metadata.translatedTitle = LAMS.translate(metadata.titleKey);
            metadata.translatedDescription = LAMS.translate(metadata.descriptionKey);
            metadata.translatedCategory = LAMS.translate(metadata.category);
            metadata.translatedLocation = LAMS.translate(metadata.location);

            titleField.setText(metadata.translatedTitle);
            descriptionField.setText(metadata.translatedDescription);
            locationField.setText(metadata.translatedLocation);
            categoryField.setText(metadata.translatedCategory);
        } else {
            LAMSMetadata.setSelected(true);
            LAMSMetadata.setEnabled(true);
            StringMetadata.setEnabled(false);
        }

        if (metadata.userCreatedDetails != null && metadata.titleKey == 0 && metadata.descriptionKey == 0) {
            StringMetadata.setEnabled(true);
            StringMetadata.setSelected(true);
            if (metadata.userCreatedDetails.name != null)
                titleField.setText(metadata.userCreatedDetails.name);

            if (metadata.userCreatedDetails.description != null)
                descriptionField.setText(metadata.userCreatedDetails.description);

            locationField.setText("");
            categoryField.setText("");
        }
    }

    public void loadImage(ResourceDescriptor resource, RPlan item) {
        // TODO: FIX LOADING IMAGE PREVIEWS
        // if (resource == null) return;
        // iconField.setText(resource.toString());
        // FileEntry entry = ResourceSystem.findEntry(resource);
        
        // if (entry == null) return;

        // RTexture texture = entry.getResource("texture");
        // if (entry != null && texture != null)
        //     setImage(texture.getImageIcon(320, 320));
        // else {
        //     byte[] data = ResourceSystem.extract(resource);
        //     if (data == null) return;
        //     texture = new RTexture(data);
        //     if (entry != null) entry.setResource("texture", texture);
        //     if (texture.parsed == true)
        //         if (ResourceSystem.getSelected().getEntry().<RPlan>getResource("item") == item)
        //             setImage(texture.getImageIcon(320, 320));
        // }
    }

    public void setImage(ImageIcon image) {
        if (image == null) {
            texture.setText("No preview to be displayed");
            texture.setIcon(null);
        } else {
            texture.setText(null);
            texture.setIcon(image);
        }
    }

    public void setEditorPanel(FileNode node) {
        FileEntry entry = node.getEntry();
        
        if (entry == null) {
            entryTable.setValueAt(node.getFilePath() + node.getName(), 0, 1);
            for (int i = 1; i < 8; ++i)
                entryTable.setValueAt("N/A", i, 1);
            return;
        }

        entryTable.setValueAt(entry.getPath(), 0, 1);

        if (entry instanceof FileDBRow) {
            Timestamp timestamp = new Timestamp(((FileDBRow)entry).getDate() * 1000L);
            entryTable.setValueAt(timestamp.toString(), 1, 1);
        } else entryTable.setValueAt("N/A", 1, 1);
        entryTable.setValueAt(entry.getSHA1(), 2, 1);
        entryTable.setValueAt(entry.getSize(), 3, 1);

        GUID guid = (entry instanceof FileDBRow) ? ((FileDBRow)entry).getGUID() : null;
        if (guid != null) {
            entryTable.setValueAt(guid.toString(), 4, 1);
            entryTable.setValueAt(Bytes.toHex((int) guid.getValue()), 5, 1);
            // TODO: Fix ULEB128 table
            //entryTable.setValueAt(Bytes.toHex(Bytes.encode(entry.GUID)), 6, 1);
            entryTable.setValueAt("N/A", 6, 1);
        } else {
            entryTable.setValueAt("N/A", 4, 1);
            entryTable.setValueAt("N/A", 5, 1);
            entryTable.setValueAt("N/A", 6, 1);
        }

        ResourceInfo info = entry.getInfo();
        if (info != null && info.getType() != ResourceType.INVALID && info.getRevision() != null)
            entryTable.setValueAt(Bytes.toHex(info.getRevision().getHead()), 7, 1);
        else entryTable.setValueAt("N/A", 7,  1);
    }

    public void setHexEditor(byte[] bytes) {
        if (bytes == null) {
            hex.setData(null);
            hex.setDefinitionStatus(JHexView.DefinitionStatus.UNDEFINED);
            hex.setEnabled(false);
        } else {
            hex.setData(new SimpleDataProvider(bytes));
            hex.setDefinitionStatus(JHexView.DefinitionStatus.DEFINED);
            hex.setEnabled(true);
        }
        hex.repaint();
    }
    
    public Toolkit run(String args[]) {
        for (String arg : args) {
            if (arg.endsWith(".farc"))
                ArchiveCallbacks.loadFileArchive(new File(arg));
            if (arg.endsWith(".map"))
                DatabaseCallbacks.loadFileDB(new File(arg));
            if (arg.contains("bigfart"))
                ProfileCallbacks.loadProfile(new File(arg));
        }
        return this;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JMenu FARMenu;
    private javax.swing.JRadioButton LAMSMetadata;
    private javax.swing.JMenu MAPMenu;
    private javax.swing.JMenu ProfileMenu;
    private javax.swing.JRadioButton StringMetadata;
    private javax.swing.JMenuItem addFile;
    private javax.swing.JMenuItem addFolder;
    private javax.swing.JTextField categoryField;
    private javax.swing.JLabel categoryLabel;
    private javax.swing.JMenuItem changeGUID;
    private javax.swing.JMenuItem changeHash;
    private javax.swing.JMenuItem clear;
    private javax.swing.JMenuItem closeTab;
    private javax.swing.JMenuItem collectAllItemDependencies;
    private javax.swing.JMenuItem collectAllLevelDependencies;
    private javax.swing.JMenu collectionD;
    private javax.swing.JMenu collectorPresets;
    private javax.swing.JTextArea console;
    private javax.swing.JScrollPane consoleContainer;
    private javax.swing.JPopupMenu consolePopup;
    private javax.swing.JMenuItem convertTexture;
    private javax.swing.JMenuItem createFileArchive;
    private javax.swing.JTextField creatorField;
    private javax.swing.JLabel creatorLabel;
    private javax.swing.JMenuItem customCollector;
    private javax.swing.JMenuItem debugLoadProfileBackup;
    public javax.swing.JMenu debugMenu;
    private javax.swing.JMenuItem decompressResource;
    private javax.swing.JMenuItem deleteContext;
    private javax.swing.JMenu dependencyGroup;
    public javax.swing.JTree dependencyTree;
    private javax.swing.JScrollPane dependencyTreeContainer;
    private javax.swing.JTextArea descriptionField;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JSplitPane details;
    private javax.swing.JMenuItem dumpHashes;
    private javax.swing.JMenuItem dumpRLST;
    private javax.swing.JPopupMenu.Separator dumpSep;
    private javax.swing.JMenuItem duplicateContext;
    private javax.swing.JMenuItem editItemContext;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenu editMenuContext;
    private javax.swing.JMenuItem editMenuDelete;
    private javax.swing.JMenuItem editProfileItems;
    private javax.swing.JMenuItem editProfileSlots;
    private javax.swing.JMenuItem editSlotContext;
    private javax.swing.JPopupMenu entryContext;
    private javax.swing.JSplitPane entryData;
    public javax.swing.JTabbedPane entryModifiers;
    public javax.swing.JTable entryTable;
    private javax.swing.JMenuItem exportAnimation;
    private javax.swing.JMenuItem exportAsBackup;
    private javax.swing.JMenuItem exportAsBackupGUID;
    private javax.swing.JMenuItem exportAsMod;
    private javax.swing.JMenuItem exportAsModCustom;
    private javax.swing.JMenuItem exportAsModGUID;
    private javax.swing.JMenu exportBackupGroup;
    private javax.swing.JMenuItem exportDDS;
    private javax.swing.JMenuItem exportGLTF;
    private javax.swing.JMenu exportGroup;
    private javax.swing.JMenuItem exportLAMSContext;
    private javax.swing.JMenu exportModGroup;
    private javax.swing.JMenu exportModelGroup;
    private javax.swing.JMenu exportOBJ;
    private javax.swing.JMenuItem exportOBJTEXCOORD0;
    private javax.swing.JMenuItem exportOBJTEXCOORD1;
    private javax.swing.JMenuItem exportOBJTEXCOORD2;
    private javax.swing.JMenuItem exportPNG;
    private javax.swing.JMenu exportTextureGroupContext;
    private javax.swing.JMenuItem extractBigProfile;
    private javax.swing.JMenuItem extractContext;
    private javax.swing.JMenu extractContextMenu;
    private javax.swing.JMenuItem extractDecompressedContext;
    private javax.swing.JMenuItem fileArchiveIntegrityCheck;
    public javax.swing.JTabbedPane fileDataTabs;
    public javax.swing.JMenu fileMenu;
    private javax.swing.JMenu gamedataMenu;
    private javax.swing.JMenuItem generateDiff;
    private tv.porst.jhexview.JHexView hex;
    private javax.swing.JTextField iconField;
    private javax.swing.JLabel iconLabel;
    private javax.swing.JMenuItem installProfileMod;
    private javax.swing.JPanel itemMetadata;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator10;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JPopupMenu.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JPopupMenu.Separator jSeparator9;
    private javax.swing.JMenuItem loadArchive;
    public javax.swing.JMenuItem loadBigProfile;
    public javax.swing.JMenuItem loadDB;
    private javax.swing.JMenu loadGroupMenu;
    private javax.swing.JMenuItem loadLAMSContext;
    private javax.swing.JMenuItem loadMod;
    private javax.swing.JTextField locationField;
    private javax.swing.JLabel locationLabel;
    private javax.swing.JMenuItem manageArchives;
    private javax.swing.JMenuItem manageProfile;
    private javax.swing.JMenu menuFileMenu;
    private javax.swing.JMenuItem mergeFARCs;
    private javax.swing.ButtonGroup metadataButtonGroup;
    public javax.swing.JMenu modMenu;
    public javax.swing.JMenu newFileDBGroup;
    private javax.swing.JMenuItem newFolderContext;
    private javax.swing.JMenu newGamedataGroup;
    private javax.swing.JMenuItem newItemContext;
    private javax.swing.JMenuItem newLegacyDB;
    private javax.swing.JMenuItem newMod;
    private javax.swing.JMenuItem newModernDB;
    private javax.swing.JMenuItem newVitaDB;
    private javax.swing.JMenuItem openCompressinator;
    public javax.swing.JMenuItem openModMetadata;
    private javax.swing.JComboBox<String> pageCombo;
    private javax.swing.JMenuItem patchMAP;
    public javax.swing.JSplitPane preview;
    private javax.swing.JSplitPane previewContainer;
    public javax.swing.JProgressBar progressBar;
    private javax.swing.JMenuItem reboot;
    private javax.swing.JMenuItem removeDependencies;
    private javax.swing.JMenuItem removeMissingDependencies;
    private javax.swing.JMenuItem renameFolder;
    private javax.swing.JMenuItem renameItemContext;
    private javax.swing.JMenuItem replaceCompressed;
    private javax.swing.JMenu replaceContext;
    private javax.swing.JMenuItem replaceDecompressed;
    private javax.swing.JMenuItem replaceDependencies;
    private javax.swing.JMenuItem replaceImage;
    public javax.swing.JMenuItem saveAs;
    private javax.swing.JPopupMenu.Separator saveDivider;
    public javax.swing.JMenuItem saveMenu;
    public javax.swing.JMenu savedataMenu;
    public javax.swing.JTextField search;
    private javax.swing.JTextField subCombo;
    private javax.swing.JMenuItem swapProfilePlatform;
    private javax.swing.JScrollPane tableContainer;
    public javax.swing.JLabel texture;
    private javax.swing.JTextField titleField;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JMenuBar toolkitMenu;
    private javax.swing.JMenu toolsMenu;
    private javax.swing.JSplitPane treeContainer;
    private javax.swing.JSplitPane workspaceDivider;
    private javax.swing.JMenuItem zeroContext;
    // End of variables declaration//GEN-END:variables
}
