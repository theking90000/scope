package be.theking90000.scope.benchmarks;

import be.theking90000.scope.Scope;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class SyntheticScopeBenchmarks {
    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class BatchState {
        @Param({"10000", "100000"})
        public int count;

        Scope<SyntheticBeans.RootContext> root;

        @Setup(Level.Invocation)
        public void setup() {
            root = rootScope();
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            root.close();
        }
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class DepthState {
        @Param({"1", "4", "8", "16", "32"})
        public int depth;

        Scope<SyntheticBeans.RootContext> root;
        Scope<?> leaf;

        @Setup(Level.Invocation)
        public void setup() {
            root = rootScope();
            leaf = root;

            for (int i = 0; i < depth; i++) {
                Scope<SyntheticBeans.NestedContext> next =
                    new Scope<>(new SyntheticBeans.NestedContext(i));
                next.ownedBy(leaf);
                leaf = next;
            }
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            root.close();
        }
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ChainDepthState {
        @Param({"1", "4", "8", "32", "128", "1024"})
        public int depth;
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class HookState {
        @Param({"false", "true"})
        public boolean enabled;

        Scope<SyntheticBeans.RootContext> root;
        Scope<SyntheticBeans.PlayerContext> player;
        SyntheticBeans.HookCounters counters;

        @Setup(Level.Invocation)
        public void setup() {
            root = rootScope();
            counters = new SyntheticBeans.HookCounters();

            if (enabled) {
                root.seed(CountingHook.class, new CountingHook(counters));
                root.addHook(CountingHook.class);
            }

            player = playerScope(root, 1);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            root.close();
        }
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class RoutingState {
        @Param({"10000", "100000"})
        public int count;

        Scope<SyntheticBeans.RootContext> root;
        Map<Integer, Scope<SyntheticBeans.PlayerContext>> scopes;
        Map<Integer, SyntheticBeans.Handler> handlers;
        int cursor;

        @Setup(Level.Trial)
        public void setup() {
            root = rootScope();
            scopes = new HashMap<>(capacityFor(count));
            handlers = new HashMap<>(capacityFor(count));

            for (int i = 0; i < count; i++) {
                Scope<SyntheticBeans.PlayerContext> player = playerScope(root, i);
                SyntheticBeans.Handler handler = player.get(SyntheticBeans.Handler.class);
                scopes.put(i, player);
                handlers.put(i, handler);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            root.close();
        }

        int nextId() {
            int id = cursor++;
            if (cursor == count) {
                cursor = 0;
            }
            return id;
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void createAndCloseEmptyScopes(BatchState state) {
        for (int i = 0; i < state.count; i++) {
            Scope<SyntheticBeans.PlayerContext> player = playerScope(state.root, i);
            player.close();
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void createAndCloseScopesWithEightBeans(BatchState state, Blackhole blackhole) {
        for (int i = 0; i < state.count; i++) {
            Scope<SyntheticBeans.PlayerContext> player = playerScope(state.root, i);
            blackhole.consume(player.get(SyntheticBeans.Bean07.class).checksum());
            player.close();
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void createAndCloseScopesWithThirtyTwoBeans(BatchState state, Blackhole blackhole) {
        for (int i = 0; i < state.count; i++) {
            Scope<SyntheticBeans.PlayerContext> player = playerScope(state.root, i);
            blackhole.consume(player.get(SyntheticBeans.Bean31.class).checksum());
            player.close();
        }
    }

    @Benchmark
    public Object resolveInheritedBean(DepthState state) {
        return state.leaf.get(SyntheticBeans.GlobalService.class);
    }

    @Benchmark
    public void createAndCloseNestedChain(ChainDepthState state) {
        Scope<SyntheticBeans.RootContext> root = rootScope();
        Scope<?> leaf = root;

        for (int i = 0; i < state.depth; i++) {
            Scope<SyntheticBeans.NestedContext> next =
                new Scope<>(new SyntheticBeans.NestedContext(i));
            next.ownedBy(leaf);
            leaf = next;
        }

        root.close();
    }

    @Benchmark
    public Object materializeBeanWithOptionalHook(HookState state) {
        return state.player.get(SyntheticBeans.HookTarget.class);
    }

    @Benchmark
    public long routeByIdThenCallDirectHandler(RoutingState state) {
        return state.handlers.get(state.nextId()).onEvent();
    }

    @Benchmark
    public Object routeByIdThenResolveLocalHandler(RoutingState state) {
        return state.scopes.get(state.nextId()).get(SyntheticBeans.Handler.class);
    }

    private static Scope<SyntheticBeans.RootContext> rootScope() {
        Scope<SyntheticBeans.RootContext> root =
            new Scope<>(new SyntheticBeans.RootContext("root"));
        root.seed(SyntheticBeans.GlobalService.class, new SyntheticBeans.GlobalService());
        return root;
    }

    private static Scope<SyntheticBeans.PlayerContext> playerScope(
        Scope<?> parent,
        int id
    ) {
        Scope<SyntheticBeans.PlayerContext> player =
            new Scope<>(new SyntheticBeans.PlayerContext(id));
        player.ownedBy(parent);
        return player;
    }

    private static int capacityFor(int expectedSize) {
        return (int) Math.ceil(expectedSize / 0.75d) + 1;
    }
}
