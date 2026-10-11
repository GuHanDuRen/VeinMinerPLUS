import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

// Runs extracted production selection/persistence methods with real Gson and an isolated config directory.
public final class XrayColorSelectionRegression {
    private static final Set<String> SELECTION_FIELDS = Set.of("SLOT_COUNT", "DEFAULT_COLOR", "SELECTION",
            "SELECTED_BLOCK_COLORS", "selectionLoaded");
    private static final Set<String> SELECTION_METHODS = Set.of("selection", "setSelection", "applySelection",
            "selectionFile", "loadSelection", "saveSelection", "blockForStack", "canAccept",
            "hasSelection", "colorForBlock");
    private static final Set<String> FAMILY_FIELDS = Set.of("ORE_SUFFIX", "HOST_STONES");
    private static final String FIXTURE = """
        import java.util.*;
        import java.io.*;
        import java.nio.file.*;
        import com.google.gson.*;
        public class XrayColorSelectionFixture {
            record ResourceLocation(String namespace, String path) {
                String getNamespace() { return namespace; }
                String getPath() { return path; }
                public String toString() { return namespace + ":" + path; }
                static ResourceLocation tryParse(String id) {
                    String[] parts = id.split(":", -1);
                    if (parts.length != 2 || !parts[0].matches("[a-z0-9_.-]+")
                            || !parts[1].matches("[a-z0-9_./-]+")) return null;
                    return new ResourceLocation(parts[0], parts[1]);
                }
            }
            static final class Item {
                final ResourceLocation id;
                final boolean air;
                Item(ResourceLocation id, boolean air) { this.id = id; this.air = air; }
                ItemStack getDefaultInstance() { return new ItemStack(this, air ? 0 : 1); }
            }
            record Block(ResourceLocation id, Item item) { Item asItem() { return item; } }
            static final class BlockRegistry extends ArrayList<Block> {
                ResourceLocation getKey(Block block) { return contains(block) ? block.id() : null; }
            }
            static final class ItemRegistry extends HashMap<ResourceLocation, Item> {
                final Item air = new Item(new ResourceLocation("minecraft", "air"), true);
                Item get(ResourceLocation id) { return getOrDefault(id, air); }
                ResourceLocation getKey(Item item) { return item.id; }
            }
            static final class BuiltInRegistries {
                static final BlockRegistry BLOCK = new BlockRegistry();
                static final ItemRegistry ITEM = new ItemRegistry();
            }
            static final class ItemStack {
                static final ItemStack EMPTY = new ItemStack(BuiltInRegistries.ITEM.air, 0);
                final Item item;
                int count;
                ItemStack(Item item, int count) { this.item = item; this.count = count; }
                boolean isEmpty() { return count <= 0 || item.air; }
                Item getItem() { return item; }
                int getCount() { return count; }
                void setCount(int count) { this.count = count; }
                ItemStack copy() { return new ItemStack(item, count); }
                ItemStack copyWithCount(int count) { return new ItemStack(item, count); }
            }
            static final class FMLPaths {
                static final FMLPaths CONFIGDIR = new FMLPaths();
                static Path path;
                Path get() { return path; }
            }
            static final class VeinMinerPlus {
                static final VeinMinerPlus LOGGER = new VeinMinerPlus();
                int warnings;
                void warn(String message, Object... args) { warnings++; }
            }
            static final class OreFamily { $FAMILY$ }
            static int invalidations;
            static void invalidateScan() { invalidations++; }
            $SELECTION$
            static int assertions;
            static final int[] EXPECTED_DEFAULTS = {
                    0xFFAA00, 0xFF5555, 0xFFFF55, 0x55FF55, 0x55FFFF,
                    0x5599FF, 0xAA55FF, 0xFF55AA, 0xFFFFFF
            };
            static void check(boolean condition, String label) {
                assertions++;
                if (!condition) throw new AssertionError(label);
                System.out.println("PASS: " + label);
            }
            static ItemStack register(String path) {
                ResourceLocation id = new ResourceLocation("alltheores", path);
                Item item = new Item(id, false);
                BuiltInRegistries.ITEM.put(id, item);
                BuiltInRegistries.BLOCK.add(new Block(id, item));
                return new ItemStack(item, 64);
            }
            static List<Selection> emptySlots() {
                List<Selection> result = new ArrayList<>();
                for (int i = 0; i < 9; i++) result.add(new Selection(ItemStack.EMPTY, EXPECTED_DEFAULTS[i]));
                return result;
            }
            static boolean hasDefaultColors(List<Selection> entries, int from) {
                for (int i = from; i < 9; i++) {
                    if (entries.get(i).color() != EXPECTED_DEFAULTS[i]) return false;
                }
                return true;
            }
            static JsonArray legacySlots(boolean customFirst) {
                JsonArray entries = new JsonArray();
                for (int i = 0; i < 9; i++) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("color", i == 0 && customFirst ? 0xFFAA00 : 0xFF5555);
                    if (i == 0) entry.addProperty("item", "alltheores:uranium_ore");
                    if (i == 3) entry.addProperty("item", "alltheores:nether_uranium_ore");
                    entries.add(entry);
                }
                return entries;
            }
            static void resetMemory() {
                SELECTION.clear();
                SELECTED_BLOCK_COLORS.clear();
                selectionLoaded = false;
            }
            static boolean matches(List<Selection> a, List<Selection> b) {
                if (a.size() != b.size()) return false;
                for (int i = 0; i < a.size(); i++) {
                    if (a.get(i).color() != b.get(i).color()) return false;
                    ItemStack x = a.get(i).stack(), y = b.get(i).stack();
                    if (x.isEmpty() != y.isEmpty()) return false;
                    if (!x.isEmpty() && (x.getItem() != y.getItem() || x.getCount() != y.getCount())) return false;
                }
                return true;
            }
            public static int run(Path config) throws Exception {
                FMLPaths.path = config;
                ItemStack uranium = register("uranium_ore");
                ItemStack nether = register("nether_uranium_ore");
                ItemStack deepslate = register("deepslate_uranium_ore");
                Block uraniumBlock = blockForStack(uranium);
                Block netherBlock = blockForStack(nether);
                Block deepslateBlock = blockForStack(deepslate);
                List<Selection> initial = selection();
                check(initial.size() == 9 && initial.stream().allMatch(entry -> entry.stack().isEmpty())
                        && hasDefaultColors(initial, 0) && !hasSelection(), "missing config gives nine empty slots with their own preset colors");
                check(initial.stream().map(Selection::color).distinct().count() == 9,
                        "all nine initial colors are different");
                check(!Files.exists(selectionFile()), "reading absent config does not write it");

                Files.writeString(selectionFile(), new Gson().toJson(legacySlots(false)));
                resetMemory();
                List<Selection> migrated = selection();
                check(hasDefaultColors(migrated, 0), "legacy all-red slots migrate to nine distinct defaults");
                check(migrated.get(0).stack().getItem() == uranium.getItem()
                        && migrated.get(1).stack().isEmpty() && migrated.get(2).stack().isEmpty()
                        && migrated.get(3).stack().getItem() == nether.getItem(),
                        "legacy migration does not move selected items or fill empty slots");
                check(colorForBlock(uraniumBlock) == EXPECTED_DEFAULTS[0]
                        && colorForBlock(netherBlock) == EXPECTED_DEFAULTS[3],
                        "migrated slot colors immediately rebuild the exact render mappings");
                Files.writeString(selectionFile(), new Gson().toJson(legacySlots(true)));
                resetMemory();
                check(hasDefaultColors(selection(), 0),
                        "current orange-first and eight-red config becomes nine distinct colors");
                JsonArray legacyCustom = legacySlots(false);
                legacyCustom.get(0).getAsJsonObject().addProperty("color", 0x123456);
                legacyCustom.get(6).getAsJsonObject().addProperty("color", 0xABCDEF);
                Files.writeString(selectionFile(), new Gson().toJson(legacyCustom));
                resetMemory();
                check(selection().get(0).color() == 0x123456 && selection().get(6).color() == 0xABCDEF
                        && selection().get(8).color() == EXPECTED_DEFAULTS[8],
                        "legacy non-red custom colors survive while default red is migrated");
                List<Selection> redCustom = selection();
                redCustom.set(4, new Selection(deepslate.copyWithCount(1), 0xFF5555));
                setSelection(redCustom);
                JsonArray marked = JsonParser.parseString(Files.readString(selectionFile())).getAsJsonArray();
                check(marked.asList().stream().allMatch(element -> element.getAsJsonObject().has("colorDefaultsVersion")
                        && element.getAsJsonObject().get("colorDefaultsVersion").getAsInt() == 1),
                        "saved slots mark the default-color version for future loads");
                resetMemory();
                check(matches(selection(), redCustom) && colorForBlock(deepslateBlock) == 0xFF5555,
                        "an explicitly chosen red color survives save and reload without migrating");
                setSelection(selection());
                resetMemory();
                check(matches(selection(), redCustom), "repeated save and reload keep all colors and slot positions stable");

                List<Selection> sparse = emptySlots();
                sparse.set(0, new Selection(ItemStack.EMPTY, 0xABCDEF));
                sparse.set(1, new Selection(uranium, 0xE01020));
                sparse.set(3, new Selection(nether, 0x2030F0));
                sparse.set(8, new Selection(ItemStack.EMPTY, 0x010203));
                int before = invalidations;
                setSelection(sparse);
                List<Selection> saved = selection();
                check(saved.size() == 9 && saved.get(0).stack().isEmpty() && saved.get(2).stack().isEmpty()
                        && saved.get(1).stack().getItem() == uranium.getItem()
                        && saved.get(3).stack().getItem() == nether.getItem(), "sparse selection retains all nine slot positions");
                check(saved.get(0).color() == 0xABCDEF && saved.get(8).color() == 0x010203,
                        "empty slots retain their custom colors");
                check(SELECTED_BLOCK_COLORS.keySet().equals(Set.of(uraniumBlock, netherBlock)),
                        "selected host variants remain two exact scan targets");
                check(colorForBlock(uraniumBlock) == 0xE01020 && colorForBlock(netherBlock) == 0x2030F0,
                        "explicit variants in the same family retain distinct exact colors");
                check(!SELECTED_BLOCK_COLORS.containsKey(deepslateBlock)
                        && colorForBlock(deepslateBlock) == DEFAULT_COLOR,
                        "unselected host variant is excluded and inherits no slot color");
                check(invalidations == before + 1, "saving invalidates the scan once");
                check(saved.get(1).stack().getCount() == 1 && uranium.getCount() == 64,
                        "saved stacks have count one without modifying inventory stacks");
                uranium.setCount(12);
                saved.get(1).stack().setCount(99);
                saved.clear();
                check(selection().get(1).stack().getCount() == 1 && selection().size() == 9,
                        "input stacks and returned stacks/list cannot mutate saved selection");

                JsonArray json = JsonParser.parseString(Files.readString(selectionFile())).getAsJsonArray();
                check(json.size() == 9 && !json.get(0).getAsJsonObject().has("item")
                        && json.get(0).getAsJsonObject().get("color").getAsInt() == 0xABCDEF
                        && json.get(1).getAsJsonObject().get("item").getAsString().equals("alltheores:uranium_ore"),
                        "real Gson writes nine slots with item identifiers and empty-slot colors");
                List<Selection> expected = selection();
                resetMemory();
                check(matches(selection(), expected), "JSON reload restores exact slot positions, items and colors");
                check(hasSelection() && SELECTED_BLOCK_COLORS.size() == 2
                        && colorForBlock(uraniumBlock) == 0xE01020 && colorForBlock(netherBlock) == 0x2030F0
                        && !SELECTED_BLOCK_COLORS.containsKey(deepslateBlock), "JSON reload rebuilds only exact color mappings");

                List<Selection> capacity = new ArrayList<>();
                for (int i = 0; i < 10; i++) capacity.add(new Selection(register("sample" + i + "_ore"), 0x100000 + i));
                setSelection(capacity);
                check(selection().size() == 9 && SELECTED_BLOCK_COLORS.size() == 9
                        && selection().get(8).stack().getItem() == capacity.get(8).stack().getItem()
                        && !SELECTED_BLOCK_COLORS.containsKey(blockForStack(capacity.get(9).stack())),
                        "tenth slot is excluded from saved selection and exact scan targets");
                check(JsonParser.parseString(Files.readString(selectionFile())).getAsJsonArray().size() == 9,
                        "JSON never writes more than nine slots");

                ItemStack stone = register("stone");
                ItemStack nonBlock = new ItemStack(new Item(new ResourceLocation("test", "non_block"), false), 1);
                check(!canAccept(null) && !canAccept(ItemStack.EMPTY) && !canAccept(stone) && !canAccept(nonBlock),
                        "null, empty, non-ore and non-block stacks are rejected");
                setSelection(Arrays.asList(new Selection(null, 0x010101), new Selection(stone, 0x020202),
                        new Selection(nonBlock, 0x030303), new Selection(nether, 0xAA445566)));
                check(selection().get(0).stack().isEmpty() && selection().get(1).stack().isEmpty()
                        && selection().get(2).stack().isEmpty() && selection().get(3).stack().getItem() == nether.getItem()
                        && selection().get(0).color() == 0x010101 && selection().get(1).color() == 0x020202,
                        "invalid inputs become colored empty slots without shifting later items");
                check(selection().get(3).color() == 0x445566 && colorForBlock(netherBlock) == 0x445566,
                        "colors are normalized to 24-bit RGB");
                check(selection().get(8).stack().isEmpty() && hasDefaultColors(selection(), 4),
                        "short selections are padded with each empty slot's own preset color");
                check(SELECTED_BLOCK_COLORS.keySet().equals(Set.of(netherBlock)),
                        "replacing selection removes previous exact targets");

                setSelection(List.of(new Selection(nether, 0x111111), new Selection(nether, 0x222222),
                        new Selection(uranium, 0x333333)));
                check(colorForBlock(netherBlock) == 0x111111 && colorForBlock(uraniumBlock) == 0x333333
                        && !SELECTED_BLOCK_COLORS.containsKey(deepslateBlock),
                        "duplicate exact blocks use their first slot color without selecting other variants");
                setSelection(List.of());
                check(!hasSelection() && selection().size() == 9
                        && selection().stream().allMatch(entry -> entry.stack().isEmpty())
                        && SELECTED_BLOCK_COLORS.isEmpty() && hasDefaultColors(selection(), 0),
                        "empty selection initializes all nine preset colors and clears exact targets");
                resetMemory();
                check(!hasSelection() && selection().size() == 9, "cleared selection remains cleared after JSON reload");

                JsonArray custom = new JsonArray();
                JsonObject removed = new JsonObject();
                removed.addProperty("item", "missing:uranium_ore");
                removed.addProperty("color", 0x123456);
                custom.add(removed);
                JsonObject known = new JsonObject();
                known.addProperty("item", "alltheores:nether_uranium_ore");
                custom.add(known);
                Files.writeString(selectionFile(), new Gson().toJson(custom));
                resetMemory();
                check(selection().get(0).stack().isEmpty() && selection().get(0).color() == 0x123456
                        && selection().get(1).stack().getItem() == nether.getItem()
                        && hasDefaultColors(selection(), 1) && selection().size() == 9,
                        "removed items retain their color and missing colors and slots use their own presets");
                check(VeinMinerPlus.LOGGER.warnings == 0, "valid persistence operations emit no warnings");
                System.out.println("Checked " + assertions + " color-selection assertions using config " + config);
                return assertions;
            }
        }
        """;

    private static SimpleJavaFileObject source(String name, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
        };
    }

    private static String extract(Path path, Set<String> fields, Set<String> methods, boolean selection) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        StringBuilder production = new StringBuilder();
        Set<String> extractedFields = new HashSet<>();
        Set<String> extractedMethods = new HashSet<>();
        boolean[] extractedRecord = {false};
        String original = Files.readString(path);
        JavacTask parser = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null,
                List.of(source(path.getFileName().toString().replace(".java", ""), original)));
        var positions = Trees.instance(parser).getSourcePositions();
        for (var tree : parser.parse()) {
            new TreeScanner<Void, Void>() {
                public Void visitClass(ClassTree type, Void ignored) {
                    if (selection && type.getSimpleName().contentEquals("Selection")) {
                        production.append(original.substring((int) positions.getStartPosition(tree, type),
                                (int) positions.getEndPosition(tree, type))).append('\n');
                        extractedRecord[0] = true;
                        return null;
                    }
                    return super.visitClass(type, ignored);
                }
                public Void visitVariable(VariableTree field, Void ignored) {
                    if (fields.contains(field.getName().toString())
                            || selection && field.getName().contentEquals("DEFAULT_COLORS")) {
                        production.append(field).append(";\n");
                        if (fields.contains(field.getName().toString())) extractedFields.add(field.getName().toString());
                    }
                    return null;
                }
                public Void visitMethod(MethodTree method, Void ignored) {
                    if (methods.contains(method.getName().toString())
                            || selection && method.getName().contentEquals("defaultColor")) {
                        production.append(method).append('\n');
                        if (methods.contains(method.getName().toString())) extractedMethods.add(method.getName().toString());
                    }
                    return null;
                }
            }.scan(tree, null);
        }
        if (!extractedFields.equals(fields) || !extractedMethods.equals(methods) || selection != extractedRecord[0]) {
            throw new AssertionError("production fields/methods/record missing from " + path);
        }
        return production.toString();
    }

    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        Path gson = Path.of(args[2]);
        String code = FIXTURE.replace("$FAMILY$", extract(Path.of(args[1]), FAMILY_FIELDS, Set.of("key"), false))
                .replace("$SELECTION$", extract(Path.of(args[0]), SELECTION_FIELDS, SELECTION_METHODS, true));
        Path output = Path.of(".compile-check", "xray-color-selection-regression");
        Files.createDirectories(output);
        Files.writeString(output.resolve("XrayColorSelectionFixture.java"), code);
        if (!compiler.getTask(null, null, null, List.of("-d", output.toString(), "-classpath", gson.toString()), null,
                List.of(source("XrayColorSelectionFixture", code))).call()) {
            throw new AssertionError("fixture compilation failed");
        }
        Path config = Files.createTempDirectory(output.toAbsolutePath(), "config-");
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL(), gson.toUri().toURL()})) {
            Object count = loader.loadClass("XrayColorSelectionFixture").getMethod("run", Path.class).invoke(null, config);
            System.out.println("PASS: " + count + " production color-selection assertions");
        }
    }
}
