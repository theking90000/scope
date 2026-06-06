package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extensive coverage of {@link OnCreatedHook} dispatch, disposers, shadowing and
 * {@link ScopeInitialization} batching.
 *
 * <p>The tests are grouped:
 * <ul>
 *     <li><b>A</b> — basic firing on constructor-injected bean creation;</li>
 *     <li><b>B</b> — disposers returned by hooks;</li>
 *     <li><b>C</b> — hook registration and resolution;</li>
 *     <li><b>D</b> — shadowing across a scope tree / multi-parent graph;</li>
 *     <li><b>E</b> — {@link ScopeInitialization} batch initialization;</li>
 *     <li><b>F</b> — edge cases.</li>
 * </ul>
 */
class HookTest {

    record Root() {}

    record Child() {}

    // ---------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------

    /** Collects creation events and an ordered free-form log. */
    static final class Recorder {
        final List<BeanCreated> created = new ArrayList<>();
        final List<String> log = new ArrayList<>();

        List<Class<?>> createdTypes() {
            List<Class<?>> types = new ArrayList<>();
            for (BeanCreated event : created) {
                types.add(event.key().type());
            }
            return types;
        }
    }

    public static class Alpha {
    }

    public static class Beta {
    }

    public static class Dependency {
    }

    public static class Dependent {
        final Dependency dependency;

        public Dependent(Dependency dependency) {
            this.dependency = dependency;
        }
    }

    /** Records every creation event; returns no disposer. */
    public static class RecordingHook implements OnCreatedHook {
        private final Recorder recorder;

        public RecordingHook(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.created.add(event);
            return null;
        }
    }

    /** Logs creation and returns a disposer that logs at close time. */
    public static class DisposingHook implements OnCreatedHook {
        private final Recorder recorder;

        public DisposingHook(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            String name = event.key().type().getSimpleName();
            recorder.log.add("created:" + name);
            return () -> recorder.log.add("disposed:" + name);
        }
    }

    // =====================================================================
    // Group A — basic firing
    // =====================================================================

    @Test
    void hookFiresWhenBeanCreatedByInjection() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        Alpha alpha = scope.get(Alpha.class);

        assertEquals(1, recorder.created.size());
        assertSame(alpha, recorder.created.get(0).bean());
    }

    @Test
    void eventCarriesOwnerKeyAndBean() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        Alpha alpha = scope.get(Alpha.class);

        BeanCreated event = recorder.created.get(0);
        assertSame(scope, event.owner());
        assertEquals(Key.of(Alpha.class), event.key());
        assertSame(alpha, event.bean());
    }

    @Test
    void seededValuesDoNotFireHook() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        scope.seed(Alpha.class, new Alpha());
        scope.get(Alpha.class); // returns the seeded value, no DI creation

        assertTrue(recorder.created.isEmpty());
    }

    @Test
    void contextAndScopeItselfDoNotFireHook() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        scope.get(Root.class);   // auto-seeded context
        scope.get(Scope.class);  // auto-seeded scope

        assertTrue(recorder.created.isEmpty());
    }

    @Test
    void scopedSingletonFiresHookExactlyOnce() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        Alpha first = scope.get(Alpha.class);
        Alpha second = scope.get(Alpha.class);

        assertSame(first, second);
        assertEquals(1, recorder.created.size());
    }

    @Test
    void distinctBeansFireSeparateEventsInCreationOrder() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        scope.get(Alpha.class);
        scope.get(Beta.class);

        assertEquals(List.of(Alpha.class, Beta.class), recorder.createdTypes());
    }

    @Test
    void dependenciesFireBeforeDependents() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        scope.get(Dependent.class);

        assertEquals(List.of(Dependency.class, Dependent.class), recorder.createdTypes());
    }

    @Test
    void hookDoesNotObserveItsOwnCreationInImmediateMode() {
        // In immediate mode the recursion guard suppresses the event emitted while
        // the hook bean itself is being created to resolve a dispatch.
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        scope.get(Alpha.class);

        assertEquals(List.of(Alpha.class), recorder.createdTypes());
        assertFalse(recorder.createdTypes().contains(RecordingHook.class));
    }

    // =====================================================================
    // Group B — disposers
    // =====================================================================

    @Test
    void disposerReturnedByHookRunsOnClose() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(DisposingHook.class);

        scope.get(Alpha.class);
        assertEquals(List.of("created:Alpha"), recorder.log);

        scope.close();
        assertEquals(List.of("created:Alpha", "disposed:Alpha"), recorder.log);
    }

    @Test
    void nullDisposerNeedsNoCleanupAndDoesNotFail() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class); // always returns null

        scope.get(Alpha.class);

        assertDoesNotThrow(scope::close);
    }

    public static class Managed {
        private final Recorder recorder;

        public Managed(Recorder recorder) {
            this.recorder = recorder;
        }

        @PreDestroy
        void stop() {
            recorder.log.add("bean:stop");
        }
    }

    @Test
    void hookDisposerRunsBeforeBeanPreDestroy() {
        // Disposers are LIFO: the bean's own @PreDestroy is registered first, the
        // hook disposer afterwards, so the hook disposer runs first.
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(DisposingHook.class);

        scope.get(Managed.class);
        scope.close();

        assertEquals(
                List.of("created:Managed", "disposed:Managed", "bean:stop"),
                recorder.log
        );
    }

    @Test
    void disposerIsRegisteredOnOwnerScopeNotHookScope() {
        // Hook registered on the parent, but the bean is created in the child.
        // The disposer must be tied to the child's (owner) lifetime.
        Scope<Root> parent = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        parent.seed(Recorder.class, recorder);
        parent.addHook(DisposingHook.class);

        Scope<Child> child = new Scope<>(new Child());
        child.ownedBy(parent);

        child.get(Alpha.class);
        assertEquals(List.of("created:Alpha"), recorder.log);

        child.close();
        assertEquals(List.of("created:Alpha", "disposed:Alpha"), recorder.log);
    }

    // =====================================================================
    // Group C — registration and resolution
    // =====================================================================

    @Test
    void addHookByQualifiedKeyResolvesQualifiedHook() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        Key<RecordingHook> key = Key.of(RecordingHook.class, "q");

        scope.seed(key, new RecordingHook(recorder));
        scope.addHook(key);

        scope.get(Alpha.class);

        assertEquals(List.of(Alpha.class), recorder.createdTypes());
    }

    public static class SelfRegisteringHook implements OnCreatedHook {
        private final Recorder recorder;

        public SelfRegisteringHook(Scope<?> scope, Recorder recorder) {
            this.recorder = recorder;
            scope.addHook(SelfRegisteringHook.class); // registers itself, by convention
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.created.add(event);
            return null;
        }
    }

    @Test
    void selfRegisteringHookActivatesViaInitialization() {
        // The documented convention: hooks register themselves in their constructor
        // and are instantiated inside an initialization session, so dispatch is
        // deferred until commit() when the hook is already a cached singleton.
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(SelfRegisteringHook.class); // constructor calls addHook
            scope.get(Alpha.class);
            init.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(recorder.createdTypes().contains(Alpha.class));
    }

    public static class HookOne implements OnCreatedHook {
        private final Recorder recorder;

        public HookOne(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.log.add("one:" + event.key().type().getSimpleName());
            return null;
        }
    }

    public static class HookTwo implements OnCreatedHook {
        private final Recorder recorder;

        public HookTwo(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.log.add("two:" + event.key().type().getSimpleName());
            return null;
        }
    }

    @Test
    void multipleHooksAllFireForOneBean() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(HookOne.class);
        scope.addHook(HookTwo.class);

        scope.get(Alpha.class);

        assertEquals(Set.of("one:Alpha", "two:Alpha"), new HashSet<>(recorder.log));
    }

    @Test
    void duplicateRegistrationOfSameHookFiresOnce() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);
        scope.addHook(RecordingHook.class); // same key registered twice

        scope.get(Alpha.class);

        assertEquals(1, recorder.created.size());
    }

    // =====================================================================
    // Group D — shadowing and graph
    // =====================================================================

    @Test
    void parentHookFiresForBeanCreatedInChild() {
        Scope<Root> parent = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        parent.seed(Recorder.class, recorder);
        parent.addHook(RecordingHook.class);

        Scope<Child> child = new Scope<>(new Child());
        child.ownedBy(parent);

        child.get(Alpha.class);

        assertEquals(List.of(Alpha.class), recorder.createdTypes());
    }

    public static class BaseHook implements OnCreatedHook {
        protected final Recorder recorder;

        public BaseHook(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.log.add("base:" + event.key().type().getSimpleName());
            return null;
        }
    }

    public static class OverrideHook extends BaseHook {
        public OverrideHook(Recorder recorder) {
            super(recorder);
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.log.add("override:" + event.key().type().getSimpleName());
            return null;
        }
    }

    @Test
    void childCanShadowParentHookKey() {
        Scope<Root> parent = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        parent.seed(Recorder.class, recorder);
        parent.addHook(BaseHook.class);

        Scope<Child> child = new Scope<>(new Child());
        child.ownedBy(parent);
        child.seed(BaseHook.class, new OverrideHook(recorder)); // shadow the key locally

        parent.get(Alpha.class); // uses BaseHook in the parent
        child.get(Beta.class);   // uses OverrideHook in the child

        assertEquals(List.of("base:Alpha", "override:Beta"), recorder.log);
    }

    @Test
    void sameHookRegisteredInChildAndParentFiresOnceForChildBean() {
        Scope<Root> parent = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        parent.seed(Recorder.class, recorder);
        parent.addHook(RecordingHook.class);

        Scope<Child> child = new Scope<>(new Child());
        child.ownedBy(parent);
        child.addHook(RecordingHook.class); // same key, collected DEEP then deduped

        child.get(Alpha.class);

        assertEquals(1, recorder.created.size());
    }

    record ParentA() {}

    record ParentB() {}

    @Test
    void multiParentCollectsHooksFromEveryVisibleParent() {
        Scope<ParentA> parentA = new Scope<>(new ParentA());
        Scope<ParentB> parentB = new Scope<>(new ParentB());
        Recorder recorder = new Recorder();
        parentA.seed(Recorder.class, recorder);
        parentA.addHook(HookOne.class);
        parentB.addHook(HookTwo.class);

        Scope<Child> child = new Scope<>(new Child());
        child.ownedBy(parentA);
        child.ownedBy(parentB);

        child.get(Alpha.class);

        assertEquals(Set.of("one:Alpha", "two:Alpha"), new HashSet<>(recorder.log));
    }

    // =====================================================================
    // Group E — ScopeInitialization batching
    // =====================================================================

    @Test
    void eventsAreBufferedUntilCommit() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(Alpha.class);
            assertTrue(recorder.created.isEmpty(), "event must be buffered, not fired");

            init.commit();
            assertEquals(1, recorder.created.size(), "commit must flush buffered events");
        }
    }

    @Test
    void commitReplaysEventsInCreationOrder() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(Dependent.class);
            scope.get(Beta.class);
            init.commit();
        }

        assertEquals(
                List.of(Dependency.class, Dependent.class, Beta.class),
                recorder.createdTypes()
        );
    }

    @Test
    void hookRegisteredAfterBeanStillSeesItAfterCommit() throws Exception {
        // Core use case: registration order does not matter inside a session.
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(Alpha.class);             // created before the hook exists
            scope.addHook(RecordingHook.class); // hook registered afterwards
            init.commit();
        }

        assertEquals(List.of(Alpha.class), recorder.createdTypes());
    }

    @Test
    void closingSessionWithoutCommitDiscardsEvents() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(Alpha.class);
            // no commit -> events discarded on close
        }

        assertTrue(recorder.created.isEmpty());
    }

    @Test
    void doubleCommitThrowsInitializationException() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        scope.seed(Recorder.class, new Recorder());

        ScopeInitialization init = scope.beginInitialization();
        init.commit();

        assertThrows(InitializationException.class, init::commit);

        init.close();
    }

    @Test
    void creationAfterSessionClosedDispatchesImmediately() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        ScopeInitialization init = scope.beginInitialization();
        init.close(); // closed without commit, session detached

        scope.get(Alpha.class);

        assertEquals(1, recorder.created.size());
    }

    @Test
    void commitInsideTryWithResourcesDoesNotDoubleFire() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(RecordingHook.class);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(Alpha.class);
            init.commit(); // fires once; the implicit close() must not refire
        }

        assertEquals(1, recorder.created.size());
    }

    @Test
    void disposersFromBufferedEventsRunOnScopeClose() throws Exception {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(DisposingHook.class);

        try (ScopeInitialization init = scope.beginInitialization()) {
            scope.get(Alpha.class);
            init.commit();
        }
        assertEquals(List.of("created:Alpha"), recorder.log);

        scope.close();
        assertEquals(List.of("created:Alpha", "disposed:Alpha"), recorder.log);
    }

    // =====================================================================
    // Group F — edge cases
    // =====================================================================

    public static class ThrowingHook implements OnCreatedHook {
        public ThrowingHook() {
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            throw new IllegalStateException("hook boom");
        }
    }

    @Test
    void exceptionFromHookPropagatesToCaller() {
        Scope<Root> scope = new Scope<>(new Root());
        scope.addHook(ThrowingHook.class);

        assertThrows(IllegalStateException.class, () -> scope.get(Alpha.class));
    }

    @Test
    void creationWithoutAnyHookSucceeds() {
        Scope<Root> scope = new Scope<>(new Root());

        Alpha alpha = scope.get(Alpha.class);

        assertNotNull(alpha);
    }

    @Test
    void hookInNonVisibleScopeDoesNotFire() {
        Scope<Root> withHook = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        withHook.seed(Recorder.class, recorder);
        withHook.addHook(RecordingHook.class);

        Scope<Child> isolated = new Scope<>(new Child()); // not attached to withHook
        isolated.get(Alpha.class);

        assertTrue(recorder.created.isEmpty());
    }

    @Test
    void distinctlyQualifiedHooksBothFire() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorderA = new Recorder();
        Recorder recorderB = new Recorder();

        Key<RecordingHook> keyA = Key.of(RecordingHook.class, "a");
        Key<RecordingHook> keyB = Key.of(RecordingHook.class, "b");
        scope.seed(keyA, new RecordingHook(recorderA));
        scope.seed(keyB, new RecordingHook(recorderB));
        scope.addHook(keyA);
        scope.addHook(keyB);

        scope.get(Alpha.class);

        assertEquals(1, recorderA.created.size());
        assertEquals(1, recorderB.created.size());
    }

    /** Creates another bean from within its own callback to exercise the recursion guard. */
    public static class CreatingHook implements OnCreatedHook {
        private final Recorder recorder;

        public CreatingHook(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Disposer onCreated(BeanCreated event) {
            recorder.created.add(event);
            if (event.key().type() == Alpha.class) {
                event.owner().get(Beta.class); // nested creation during dispatch
            }
            return null;
        }
    }

    @Test
    void nestedBeanCreatedInsideHookIsNotReDispatched() {
        Scope<Root> scope = new Scope<>(new Root());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);
        scope.addHook(CreatingHook.class);

        scope.get(Alpha.class);

        // Alpha is recorded; Beta is created but its event is suppressed by the guard.
        assertTrue(recorder.createdTypes().contains(Alpha.class));
        assertFalse(recorder.createdTypes().contains(Beta.class));
    }

    @Test
    void addHookAfterCloseThrowsScopeStateException() {
        Scope<Root> scope = new Scope<>(new Root());
        scope.close();

        assertThrows(ScopeStateException.class, () -> scope.addHook(RecordingHook.class));
    }
}
