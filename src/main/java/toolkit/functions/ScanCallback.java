package toolkit.functions;

import cwlib.util.Bytes;
import toolkit.utilities.FileChooser;
import toolkit.utilities.ResourceSystem;
import toolkit.windows.Toolkit;
import cwlib.io.streams.MemoryInputStream;
import cwlib.util.FileIO;
import cwlib.resources.RMesh;
import cwlib.types.Resource;
import cwlib.enums.Magic;
import cwlib.enums.ResourceType;
import cwlib.types.data.SHA1;
import cwlib.types.FileArchive;
import cwlib.types.FileDB;
import cwlib.types.databases.FileEntry;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ScanCallback {
    public static void scanRawData() {
        Toolkit toolkit = Toolkit.instance;
        File dump = FileChooser.openFile("data.bin", null, false);
        if (dump == null) return;

        System.out.println("Loading Image into memory, this may take a while.");

        MemoryInputStream data = new MemoryInputStream(dump.getAbsolutePath());

        System.out.println("Image loaded");


        File dumpMap = FileChooser.openFile("dump.map", "map", true);
        if (dumpMap == null) return;

        FileIO.write(new byte[] {
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00
        }, dumpMap.getAbsolutePath());

        FileDB out = new FileDB(dumpMap);

        File file = FileChooser.openFile("dump.farc", "farc", true);
        if (file == null) return;
        FileIO.write(new byte[] {
            0,
            0,
            0,
            0,
            0x46,
            0x41,
            0x52,
            0x43
        }, file.getAbsolutePath());
        FileArchive farc = new FileArchive(file);

        String[] headers = new String[60];
        Byte[] chars = new Byte[60];
        int m = 0;
        for (Magic magic: Magic.values()) {
            headers[m] = magic.value.substring(0, 3);
            chars[m] = (byte) magic.value.charAt(0);
            m++;
        }
        headers[56] = "TEX";
        chars[56] = (byte)
        "T".charAt(0);
        headers[57] = "FSB";
        chars[57] = (byte)
        "F".charAt(0);
        headers[58] = "BIK";
        chars[58] = (byte)
        "B".charAt(0);
        headers[59] = "GTF";
        chars[59] = (byte) " ".charAt(0);
        
        Set < String > HEADERS = new HashSet<String> (Arrays.asList(headers));
        Set < Byte > VALUES = new HashSet<Byte> (Arrays.asList(chars));

        toolkit.databaseService.submit(() -> {
            System.out.println("Started scanning this may take a while, please wait.");
            toolkit.progressBar.setVisible(true);
            toolkit.progressBar.setMaximum(data.length);

            int resourceCount = 0;
            int GUID = 0x00150000;
            while ((data.offset + 4) <= data.length) {
                toolkit.progressBar.setValue(data.offset + 1);
                int begin = data.offset;

                if (!VALUES.contains(data.data[data.offset])) {
                    data.offset++;
                    continue;
                }

                String magic = uncompressedData.str(3);

                if (!HEADERS.contains(magic)) {
                    uncompressedData.seek(begin + 1);
                    continue;
                }

                String type = uncompressedData.str(1);

                try {
                    byte[] buffer = null;

                    switch (type) {
                        case "i":
                            {
                                if (!magic.equals("BIK")) break;
                                int size = Integer.reverseBytes(uncompressedData.i32());
                                data.offset -= 8;
                                buffer = uncompressedData.bytes(size + 8);
                            }
                        case "t":
                            {
                                if (magic.equals("FSB") || magic.equals("TEX")) break;
                                int end = 0;
                                while ((data.offset + 4) <= data.length) {
                                    String mag = uncompressedData.str(3);
                                    if (HEADERS.contains(mag)) {
                                        String t = uncompressedData.str(1);
                                        if (t.equals(" ") || t.equals("4") || t.equals("b") || t.equals("t") || t.equals("i")) {
                                            data.offset -= 4;
                                            end = data.offset;
                                            uncompressedData.seek(begin);
                                            break;
                                        }
                                    } else data.offset -= 2;
                                }

                                buffer = uncompressedData.bytes(end - begin);

                                final String converted = new String(buffer, StandardCharsets.UTF_8);
                                final byte[] output = converted.getBytes(StandardCharsets.UTF_8);

                                if (!Arrays.equals(buffer, output))
                                    buffer = null;

                                break;
                            }


                        case "4":
                            {
                                if (!magic.equals("FSB")) break;
                                int count = Integer.reverseBytes(uncompressedData.i32());
                                uncompressedData.forward(0x4);
                                int size = Integer.reverseBytes(uncompressedData.i32());
                                uncompressedData.forward(0x20);
                                for (int i = 0; i < count; ++i)
                                    uncompressedData.forward(Short.reverseBytes(uncompressedData.i16()) - 2);
                                if (data.data[data.offset] == 0) {
                                    while (uncompressedData.i8() == 0);
                                    data.offset -= 1;
                                }
                                uncompressedData.forward(size);
                                size = data.offset - begin;
                                uncompressedData.seek(begin);
                                buffer = uncompressedData.bytes(size);
                                break;
                            }

                        case "b":
                            {
                                if (magic.equals("FSB") || magic.equals("TEX")) break;
                                int revision = uncompressedData.i32f();
                                if (revision > 0x021803F9 || revision < 0) {
                                    uncompressedData.seek(begin + 1);
                                    continue;
                                }
                                int dependencyOffset = uncompressedData.i32f();
                                uncompressedData.forward(dependencyOffset - 12);
                                int count = uncompressedData.i32f();
                                for (int j = 0; j < count; ++j) {
                                    uncompressedData.resource(ResourceType.FILE_OF_BYTES, true);
                                    uncompressedData.i32f();
                                }

                                int size = data.offset - begin;
                                uncompressedData.seek(begin);

                                buffer = uncompressedData.bytes(size);
                            }

                        case " ":
                            {
                                if (magic.equals("TEX")) uncompressedData.forward(2);
                                else if (magic.equals("GTF")) uncompressedData.forward(0x1a);
                                else break;
                                int count = uncompressedData.i16();
                                int size = 0;
                                for (int j = 0; j < count; ++j) {
                                    size += uncompressedData.i16();
                                    uncompressedData.i16();
                                }
                                uncompressedData.forward(size);


                                if (data.offset < 0 || ((data.offset + 1) >= data.length)) {
                                    uncompressedData.seek(begin + 1);
                                    continue;
                                }

                                size = data.offset - begin;
                                uncompressedData.seek(begin);
                                buffer = uncompressedData.bytes(size);
                            }


                    }

                    uncompressedData.seek(begin + 1);

                    if (buffer != null) {
                        int querySize = ((buffer.length * 10) + farc.queueSize + farc.hashTable.length + 8 + (farc.entries.size() * 0x1C)) * 2;
                        if (querySize < 0 || querySize >= Integer.MAX_VALUE) {
                            System.out.println("Ran out of memory, flushing current changes...");
                            farc.save(toolkit.progressBar);
                            toolkit.progressBar.setMaximum(data.length);
                            toolkit.progressBar.setValue(data.offset + 1);
                        }

                        resourceCount++;
                        farc.add(buffer);

                        SHA1 sha1 = SHA1.fromBuffer(buffer);
                        FileEntry entry = ResourceSystem.findEntry(sha1);
                        if (entry != null) {
                            System.out.println("Found Resource : " + entry.path + " (0x" + Bytes.toHex(begin) + ")");
                            out.add(entry);
                        }
                        //System.out.println("Found Resource : " + entry.path + " (0x" + Bytes.toHex(begin) + ")");
                        else {
                          
                            FileEntry e = new FileEntry(buffer, SHA1.fromBuffer(buffer));

                            String name = "" + begin;

                            switch (magic) {
                                case "PLN":
                                    name += ".plan";
                                    break;
                                case "LVL":
                                    name += ".bin";
                                    break;
                                default:
                                    name += "." + magic.toLowerCase();
                                    break;
                            }

                            if (magic.equals("MSH")) {
                                RMesh mesh = new RMesh("mesh", new Resource(buffer));
                                name = mesh.bones[0].name + ".mol";
                            }


                            e.path = "resources/" + magic.toLowerCase() + "/" + name;
                            e.GUID = GUID;

                            GUID++;

                            out.add(e);


                            System.out.println("Found Resource : " + magic + type + " (0x" + Bytes.toHex(begin) + ")");
                        }

                    }

                } catch (Exception e) {
                    uncompressedData.seek(begin + 1);
                }
            }


            toolkit.progressBar.setVisible(false);
            toolkit.progressBar.setMaximum(0);
            toolkit.progressBar.setValue(0);

            farc.save(toolkit.progressBar);
            out.save(out.path);
        });
    }
}