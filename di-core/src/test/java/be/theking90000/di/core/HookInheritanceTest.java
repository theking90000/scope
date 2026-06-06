package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage of the self-registering {@link OnCreatedHook} convention together with
 * inheritance-based shadowing, mirroring the Bukkit example documented on
 * {@link OnCreatedHook}.
 *
 * <p>Pattern under test:
 * <ul>
 *     <li>a base hook registers itself in its constructor via
 *         {@code s.addHook(BaseHook.class)};</li>
 *     <li>a subclass calls {@code super(s)} — which registers {@code BaseHook.class}
 *         a <em>second</em> time (intended; the {@code unique} guard in dispatch
 *         deduplicates so the hook still fires once) — and then shadows the key in
 *         its own scope via {@code s.seed(BaseHook.class, this)};</li>
 *     <li>hooks are eagerly instantiated inside {@link Scope#beginInitialization()},
 *         which is the documented way to make self-registration safe: events are
 *         buffered, so resolving the hook during its own creation cannot recurse.</li>
 * </ul>
 */
class HookInheritanceTest {

    record Root() {}

    record Player() {}

    static final class Recorder {
        final List<String> log = new ArrayList<>();
    }

    /** Marker mimicking Bukkit's {@code Listener}: only these beans are reacted to. */
    interface Listener {
    }

    /**
     * Base hook that registers itself in its constructor and reacts to {@link Listener}
     * beans created in its scope.
     */
    public static class EventHook implements OnCreatedHook {
        protected final Recorder recorder;

        public EventHook(Scope<?> scope, Recorder recorder) {
            this.recorder = recorder;
            scope.addHook(EventHook.class); // self-register by convention
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            if (event.bean() instanceof Listener) {
                String name = event.key().type().getSimpleName();
                recorder.log.add("base:" + name);
                return () -> recorder.log.add("base-dispose:" + name);
            }
            return null;
        }
    }

    /**
     * Player-scoped subclass: re-registers {@code EventHook.class} through {@code super}
     * and shadows it locally so beans created in the player scope use this instance.
     */
    public static class PlayerEventHook extends EventHook {
        public PlayerEventHook(Scope<?> scope, Recorder recorder) {
            super(scope, recorder);              // re-adds EventHook.class (deduped at dispatch)
            scope.seed(EventHook.class, this);   // shadow the hook key in this scope
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            if (event.bean() instanceof Listener) {
                String name = event.key().type().getSimpleName();
                recorder.log.add("player:" + name);
                return () -> recorder.log.add("player-dispose:" + name);
            }
            return null;
        }
    }

    public static class GlobalListener implements Listener {
    }

    public static class PlayerListener implements Listener {
    }

    /** Eagerly instantiate every type inside a committed init session. */
    private static void initialize(Scope<?> scope, Class<?>... types) {
        try (ScopeInitialization init = scope.beginInitialization()) {
            for (Class<?> type : types) {
                scope.get(type);
            }
            init.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void selfRegisteringHookFiresForListenersInItsScope() {
        Scope<Root> root = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        root.seed(Recorder.class, recorder);

        initialize(root, EventHook.class, GlobalListener.class);

        assertEquals(List.of("base:GlobalListener"), recorder.log);
    }

    @Test
    void subclassShadowsBaseHookForBeansCreatedInChildScope() {
        Scope<Root> root = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        root.seed(Recorder.class, recorder);

        initialize(root, EventHook.class, GlobalListener.class);

        Scope<Player> player = new Scope<>(new Player());
        player.ownedBy(root);
        initialize(player, PlayerEventHook.class, PlayerListener.class);

        // The global listener used the base hook; the player listener used the shadow.
        assertEquals(List.of("base:GlobalListener", "player:PlayerListener"), recorder.log);
        // The base hook never fired for the player-scoped listener.
        assertFalse(recorder.log.contains("base:PlayerListener"));
    }

    @Test
    void duplicateRegistrationThroughSuperFiresHookOnce() {
        // EventHook.class is registered in root (self) and again in the player scope
        // through super(...). The unique guard must collapse that to a single dispatch.
        Scope<Root> root = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        root.seed(Recorder.class, recorder);

        initialize(root, EventHook.class);

        Scope<Player> player = new Scope<>(new Player());
        player.ownedBy(root);
        initialize(player, PlayerEventHook.class, PlayerListener.class);

        assertEquals(1, Collections.frequency(recorder.log, "player:PlayerListener"));
    }

    @Test
    void shadowHookDisposerRunsWhenChildScopeCloses() {
        Scope<Root> root = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        root.seed(Recorder.class, recorder);

        initialize(root, EventHook.class);

        Scope<Player> player = new Scope<>(new Player());
        player.ownedBy(root);
        initialize(player, PlayerEventHook.class, PlayerListener.class);

        assertFalse(recorder.log.contains("player-dispose:PlayerListener"));

        player.close();

        assertTrue(recorder.log.contains("player-dispose:PlayerListener"));
    }

    @Test
    void shadowingIsScopeLocalSoParentBeansKeepBaseHook() {
        Scope<Root> root = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        root.seed(Recorder.class, recorder);

        initialize(root, EventHook.class);

        Scope<Player> player = new Scope<>(new Player());
        player.ownedBy(root);
        initialize(player, PlayerEventHook.class);

        // A listener created in the root scope after the child shadowed the key
        // must still be handled by the base hook, not the player one.
        initialize(root, GlobalListener.class);

        assertTrue(recorder.log.contains("base:GlobalListener"));
        assertFalse(recorder.log.contains("player:GlobalListener"));
    }
}
