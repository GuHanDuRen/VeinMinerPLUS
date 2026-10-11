import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
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

// Exercises extracted production selection and ore-family methods without launching Minecraft.
public final class XraySelectionRegression {
    private static final Set<String> SELECTION_FIELDS = Set.of("SLOT_COUNT", "SELECTED_STACKS", "SELECTED_FAMILIES");
    private static final Set<String> SELECTION_METHODS = Set.of("selectedStacks", "setSelectedStacks",
            "familyForStack", "canAccept");
    private static final Set<String> FAMILY_FIELDS = Set.of("ORE_SUFFIX", "HOST_STONES");
    private static final Set<String> FAMILY_METHODS = Set.of("key");
    private static final Set<String> OLD_FAILURES = Set.of("ordinary and nether variants survive save",
            "ordinary and nether variants survive reopening", "ordinary and deepslate variants survive save",
            "all three ore variants retain their order");
    private static final String FIXTURE = """
        import java.util.*;
        public class $CLASS$ {
            record ResourceLocation(String namespace, String path) {
                String getNamespace() { return namespace; }
                String getPath() { return path; }
            }
            static final class Item {}
            record Block(ResourceLocation id, Item item) {
                Item asItem() { return item; }
            }
            static final class Registry extends ArrayList<Block> {
                ResourceLocation getKey(Block block) { return contains(block) ? block.id() : null; }
            }
            static final class BuiltInRegistries { static final Registry BLOCK = new Registry(); }
            static final class ItemStack {
                final Item item;
                int count;
                ItemStack(Item item, int count) { this.item = item; this.count = count; }
                boolean isEmpty() { return count <= 0; }
                Item getItem() { return item; }
                int getCount() { return count; }
                void setCount(int count) { this.count = count; }
                ItemStack copy() { return new ItemStack(item, count); }
                ItemStack copyWithCount(int count) { return new ItemStack(item, count); }
            }
            static final class OreFamily {
                $FAMILY$
            }
            static int invalidations;
            static void invalidateScan() { invalidations++; }
            $SELECTION$
            static final List<String> failures = new ArrayList<>();
            static int assertions;
            static ItemStack ore(String path) {
                Block block = new Block(new ResourceLocation("alltheores", path), new Item());
                BuiltInRegistries.BLOCK.add(block);
                return new ItemStack(block.asItem(), 64);
            }
            static void check(boolean condition, String label) {
                assertions++;
                if (!condition) failures.add(label);
            }
            static boolean itemsAre(List<ItemStack> actual, ItemStack... expected) {
                if (actual.size() != expected.length) return false;
                for (int i = 0; i < expected.length; i++) {
                    if (actual.get(i).getItem() != expected[i].getItem()) return false;
                }
                return true;
            }
            public static List<String> run() {
                ItemStack uranium = ore("uranium_ore");
                ItemStack nether = ore("nether_uranium_ore");
                ItemStack deepslate = ore("deepslate_uranium_ore");
                check("alltheores:uranium".equals(familyForStack(uranium))
                        && familyForStack(uranium).equals(familyForStack(nether))
                        && familyForStack(uranium).equals(familyForStack(deepslate)),
                        "production OreFamily maps uranium host variants to one family");
                setSelectedStacks(List.of(uranium, nether));
                check(itemsAre(selectedStacks(), uranium, nether), "ordinary and nether variants survive save");
                check(SELECTED_FAMILIES.equals(Set.of("alltheores:uranium")),
                        "ordinary and nether variants use one scan family");
                setSelectedStacks(selectedStacks());
                check(itemsAre(selectedStacks(), uranium, nether), "ordinary and nether variants survive reopening");

                setSelectedStacks(List.of(uranium, deepslate));
                check(itemsAre(selectedStacks(), uranium, deepslate),
                        "ordinary and deepslate variants survive save");
                check(SELECTED_FAMILIES.size() == 1, "deepslate variant does not duplicate scan families");
                setSelectedStacks(List.of(uranium, nether, deepslate));
                check(itemsAre(selectedStacks(), uranium, nether, deepslate),
                        "all three ore variants retain their order");
                check(SELECTED_FAMILIES.size() == 1, "three ore variants still scan one family");

                int beforeClear = invalidations;
                setSelectedStacks(List.of());
                check(selectedStacks().isEmpty() && SELECTED_FAMILIES.isEmpty(),
                        "clear removes all selected stacks and scan families");
                check(invalidations == beforeClear + 1, "clear invalidates the scan");

                List<ItemStack> many = new ArrayList<>();
                for (int i = 0; i < 40; i++) many.add(ore("sample" + i + "_ore"));
                setSelectedStacks(many);
                check(selectedStacks().size() == 36 && SELECTED_FAMILIES.size() == 36,
                        "selection is limited to 36 valid slots");
                check(itemsAre(selectedStacks(), many.subList(0, 36).toArray(ItemStack[]::new))
                        && !SELECTED_FAMILIES.contains(familyForStack(many.get(36))),
                        "overflow items are excluded from saved slots and scan families");

                ItemStack stone = ore("stone");
                ItemStack empty = new ItemStack(uranium.getItem(), 0);
                ItemStack nonBlock = new ItemStack(new Item(), 1);
                check(!canAccept(null) && !canAccept(empty) && !canAccept(stone) && !canAccept(nonBlock),
                        "null empty non-ore and non-block items are rejected");
                check(canAccept(uranium) && canAccept(nether) && canAccept(deepslate),
                        "all uranium variants are accepted");
                setSelectedStacks(Arrays.asList(null, empty, stone, nonBlock, uranium));
                check(itemsAre(selectedStacks(), uranium) && SELECTED_FAMILIES.size() == 1,
                        "invalid inputs do not consume selection slots");

                check(uranium.getCount() == 64 && selectedStacks().get(0).getCount() == 1,
                        "saved copies have count one without changing the input stack");
                uranium.setCount(0);
                check(selectedStacks().get(0).getCount() == 1, "saved selection is independent of the input stack");
                List<ItemStack> returned = selectedStacks();
                returned.get(0).setCount(99);
                returned.clear();
                check(selectedStacks().size() == 1 && selectedStacks().get(0).getCount() == 1,
                        "reopened selection returns independent stacks and list");
                System.out.println("Checked " + assertions + " selection assertions.");
                return List.copyOf(failures);
            }
        }
        """;

    private static SimpleJavaFileObject source(String name, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
        };
    }

    private static String extract(Path path, Set<String> fields, Set<String> methods) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        StringBuilder production = new StringBuilder();
        Set<String> extractedFields = new HashSet<>();
        Set<String> extractedMethods = new HashSet<>();
        JavacTask parser = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null,
                List.of(source(path.getFileName().toString().replace(".java", ""), Files.readString(path))));
        for (var tree : parser.parse()) {
            new TreeScanner<Void, Void>() {
                public Void visitVariable(VariableTree field, Void ignored) {
                    if (fields.contains(field.getName().toString())) {
                        production.append(field).append(";\n");
                        extractedFields.add(field.getName().toString());
                    }
                    return null;
                }
                public Void visitMethod(MethodTree method, Void ignored) {
                    if (methods.contains(method.getName().toString())) {
                        production.append(method).append('\n');
                        extractedMethods.add(method.getName().toString());
                    }
                    return null;
                }
            }.scan(tree, null);
        }
        if (!extractedFields.equals(fields) || !extractedMethods.equals(methods)) {
            throw new AssertionError("production fields/methods missing from " + path);
        }
        return production.toString().replace("net.minecraft.core.registries.BuiltInRegistries", "BuiltInRegistries");
    }

    private static List<?> runSource(Path path, Path familyPath, String name) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        String code = FIXTURE.replace("$CLASS$", name)
                .replace("$FAMILY$", extract(familyPath, FAMILY_FIELDS, FAMILY_METHODS))
                .replace("$SELECTION$", extract(path, SELECTION_FIELDS, SELECTION_METHODS));
        Path output = Path.of(".compile-check", "xray-selection-regression");
        Files.createDirectories(output);
        Files.writeString(output.resolve(name + ".java"), code);
        if (!compiler.getTask(null, null, null, List.of("-d", output.toString()), null,
                List.of(source(name, code))).call()) throw new AssertionError("fixture compilation failed");
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
            return (List<?>) loader.loadClass(name).getMethod("run").invoke(null);
        }
    }

    public static void main(String[] args) throws Exception {
        Path family = Path.of(args[2]);
        List<?> beforeFailures = runSource(Path.of(args[0]), family, "XraySelectionFixtureBefore");
        if (!new HashSet<>(beforeFailures).equals(OLD_FAILURES)) {
            throw new AssertionError("unexpected old behavior failures: " + beforeFailures);
        }
        beforeFailures.forEach(failure -> System.out.println("REPRODUCED OLD FAILURE: " + failure));
        List<?> afterFailures = runSource(Path.of(args[1]), family, "XraySelectionFixtureAfter");
        if (!afterFailures.isEmpty()) throw new AssertionError("new behavior failures: " + afterFailures);
        System.out.println("PASS: all production selection regressions; before = " + args[0] + ", after = " + args[1]);
    }
}
