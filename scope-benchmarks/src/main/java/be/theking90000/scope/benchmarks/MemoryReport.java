package be.theking90000.scope.benchmarks;

import be.theking90000.scope.Scope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openjdk.jol.info.GraphLayout;

public final class MemoryReport {
    private static volatile Object retained;
    private static final long MIB = 1024L * 1024L;

    private MemoryReport() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Synthetic retained-heap report");
        System.out.println("JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        System.out.println("Max heap: " + humanBytes(Runtime.getRuntime().maxMemory()));
        System.out.println("Use the deltas as comparative estimates, not as divine revelation.\n");

        runScenario("100k plain context records", () -> plainContexts(100_000));
        runScenario("100k plain map<Integer, Handler>", () -> plainHandlerMap(100_000));
        runScenario("10k empty sibling scopes", () -> siblingScopes(10_000, 0));
        runScenarioIfHeapAtLeast("100k empty sibling scopes", 512, () -> siblingScopes(100_000, 0));
        runScenario("10k sibling scopes with 8 beans", () -> siblingScopes(10_000, 8));
        runScenarioIfHeapAtLeast("100k sibling scopes with 8 beans", 1_536, () -> siblingScopes(100_000, 8));
        runScenarioIfHeapAtLeast("10k sibling scopes with 32 beans", 1_024, () -> siblingScopes(10_000, 32));
        runScenarioIfHeapAtLeast("nested chain depth 1k", 256, () -> nestedChain(1_000));

        printJolSamples();
    }

    private static void runScenarioIfHeapAtLeast(
        String label,
        long requiredMiB,
        Scenario scenario
    ) throws Exception {
        if (Runtime.getRuntime().maxMemory() < requiredMiB * MIB) {
            System.out.printf("%-40s skipped; needs at least %s max heap%n",
                label,
                humanBytes(requiredMiB * MIB));
            return;
        }

        runScenario(label, scenario);
    }

    private static void runScenario(String label, Scenario scenario) throws Exception {
        retained = null;
        forceGc();
        long before = usedHeap();

        retained = scenario.create();
        forceGc();
        long afterCreate = usedHeap();

        closeRetained();
        retained = null;
        forceGc();
        long afterClose = usedHeap();

        System.out.printf("%-40s retained=%10s after-close=%10s%n",
            label,
            humanBytes(afterCreate - before),
            humanBytes(afterClose - before));
    }

    private static Object siblingScopes(int count, int beanCount) {
        Scope<SyntheticBeans.RootContext> root = rootScope();

        for (int i = 0; i < count; i++) {
            Scope<SyntheticBeans.PlayerContext> player =
                new Scope<>(new SyntheticBeans.PlayerContext(i));
            player.ownedBy(root);
            materialize(player, beanCount);
        }

        return root;
    }

    private static Object plainContexts(int count) {
        List<SyntheticBeans.PlayerContext> contexts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            contexts.add(new SyntheticBeans.PlayerContext(i));
        }
        return contexts;
    }

    private static Object plainHandlerMap(int count) {
        Map<Integer, SyntheticBeans.Handler> handlers = new HashMap<>(capacityFor(count));
        for (int i = 0; i < count; i++) {
            handlers.put(i, new SyntheticBeans.Handler());
        }
        return handlers;
    }

    private static Object nestedChain(int depth) {
        Scope<SyntheticBeans.RootContext> root = rootScope();
        Scope<?> leaf = root;

        for (int i = 0; i < depth; i++) {
            Scope<SyntheticBeans.NestedContext> child =
                new Scope<>(new SyntheticBeans.NestedContext(i));
            child.ownedBy(leaf);
            leaf = child;
        }

        return root;
    }

    private static void materialize(Scope<?> scope, int beanCount) {
        switch (beanCount) {
            case 0 -> { }
            case 8 -> scope.get(SyntheticBeans.Bean07.class);
            case 32 -> scope.get(SyntheticBeans.Bean31.class);
            default -> throw new IllegalArgumentException("Unsupported bean count: " + beanCount);
        }
    }

    private static Scope<SyntheticBeans.RootContext> rootScope() {
        Scope<SyntheticBeans.RootContext> root =
            new Scope<>(new SyntheticBeans.RootContext("root"));
        root.seed(SyntheticBeans.GlobalService.class, new SyntheticBeans.GlobalService());
        return root;
    }

    private static void printJolSamples() {
        Scope<SyntheticBeans.RootContext> root = rootScope();
        Scope<SyntheticBeans.PlayerContext> player =
            new Scope<>(new SyntheticBeans.PlayerContext(1));
        player.ownedBy(root);

        System.out.println("\nJOL sample: one empty player scope graph");
        System.out.println(GraphLayout.parseInstance(player).toFootprint());

        player.get(SyntheticBeans.Bean31.class);
        System.out.println("JOL sample: same player scope after materializing 32 beans");
        System.out.println(GraphLayout.parseInstance(player).toFootprint());

        root.close();
    }

    private static void closeRetained() {
        Object value = retained;
        if (value instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(150);
        }
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB"};
        int unit = 0;
        while (Math.abs(value) >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format("%.2f %s", value, units[unit]);
    }

    private static int capacityFor(int expectedSize) {
        return (int) Math.ceil(expectedSize / 0.75d) + 1;
    }

    @FunctionalInterface
    private interface Scenario {
        Object create();
    }
}
