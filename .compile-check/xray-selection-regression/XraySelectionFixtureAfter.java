import java.util.*;
public class XraySelectionFixtureAfter {
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
        private static final String ORE_SUFFIX = "_ore";
private static final String[] HOST_STONES = {"deepslate", "slate", "stone", "endstone", "netherrack", "nether", "end", "other", "blackstone", "basalt", "tuff", "granite", "diorite", "andesite", "marble", "limestone"};

public static String key(Block block) {
    ResourceLocation id = block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
    if (id == null) {
        return null;
    }
    String path = id.getPath();
    if (!path.endsWith(ORE_SUFFIX) || path.length() == ORE_SUFFIX.length()) {
        return null;
    }
    String base = path.substring(0, path.length() - ORE_SUFFIX.length());
    for (String host : HOST_STONES) {
        String suffix = "_" + host;
        if (base.length() > suffix.length() && base.endsWith(suffix)) {
            base = base.substring(0, base.length() - suffix.length());
            break;
        }
        String prefix = host + "_";
        if (base.length() > prefix.length() && base.startsWith(prefix)) {
            base = base.substring(prefix.length());
            break;
        }
    }
    return base.isEmpty() ? null : id.getNamespace() + ":" + base;
}

    }
    static int invalidations;
    static void invalidateScan() { invalidations++; }
    private static final int SLOT_COUNT = 36;
private static final List<ItemStack> SELECTED_STACKS = new ArrayList<>();
private static final Set<String> SELECTED_FAMILIES = new HashSet<>();

static List<ItemStack> selectedStacks() {
    List<ItemStack> copy = new ArrayList<>(SELECTED_STACKS.size());
    for (ItemStack stack : SELECTED_STACKS) {
        copy.add(stack.copy());
    }
    return copy;
}

static void setSelectedStacks(List<ItemStack> stacks) {
    SELECTED_STACKS.clear();
    SELECTED_FAMILIES.clear();
    for (ItemStack stack : stacks) {
        String family = familyForStack(stack);
        if (family != null) {
            SELECTED_FAMILIES.add(family);
            SELECTED_STACKS.add(stack.copyWithCount(1));
            if (SELECTED_STACKS.size() >= SLOT_COUNT) {
                break;
            }
        }
    }
    invalidateScan();
}

static String familyForStack(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
        return null;
    }
    for (Block block : BuiltInRegistries.BLOCK) {
        if (block.asItem() == stack.getItem()) {
            String family = OreFamily.key(block);
            if (family != null) {
                return family;
            }
        }
    }
    return null;
}

static boolean canAccept(ItemStack stack) {
    return familyForStack(stack) != null;
}

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
