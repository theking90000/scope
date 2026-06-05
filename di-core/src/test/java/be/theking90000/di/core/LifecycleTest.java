package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleTest {
    record RootScope() {}

    static class Recorder {
        private final List<String> events = new ArrayList<>();

        void add(String event) {
            events.add(event);
        }

        List<String> events() {
            return events;
        }
    }

    public static class ConstructedOnce {
        static int postConstructCalls = 0;

        public ConstructedOnce() {
        }

        @PostConstruct
        private void start() {
            postConstructCalls++;
        }
    }

    @Test
    void postConstructRunsOnceForScopedSingleton() {
        ConstructedOnce.postConstructCalls = 0;
        Scope<RootScope> scope = new Scope<>(new RootScope());

        ConstructedOnce first = scope.get(ConstructedOnce.class);
        ConstructedOnce second = scope.get(ConstructedOnce.class);

        assertEquals(first, second);
        assertEquals(1, ConstructedOnce.postConstructCalls);
    }

    public static class Destroyed {
        static int preDestroyCalls = 0;

        public Destroyed() {
        }

        @PreDestroy
        private void stop() {
            preDestroyCalls++;
        }
    }

    @Test
    void preDestroyRunsWhenScopeCloses() {
        Destroyed.preDestroyCalls = 0;
        Scope<RootScope> scope = new Scope<>(new RootScope());

        scope.get(Destroyed.class);
        scope.close();

        assertEquals(1, Destroyed.preDestroyCalls);
    }

    public static class ParentLifecycle {
        protected final Recorder recorder;

        public ParentLifecycle(Recorder recorder) {
            this.recorder = recorder;
        }

        @PostConstruct
        private void parentStart() {
            recorder.add("parent:start");
        }

        @PreDestroy
        private void parentStop() {
            recorder.add("parent:stop");
        }
    }

    public static class ChildLifecycle extends ParentLifecycle {
        public ChildLifecycle(Recorder recorder) {
            super(recorder);
        }

        @PostConstruct
        private void childStart() {
            recorder.add("child:start");
        }

        @PreDestroy
        private void childStop() {
            recorder.add("child:stop");
        }
    }

    @Test
    void inheritedPrivateHooksUseLifecycleOrder() {
        Scope<RootScope> scope = new Scope<>(new RootScope());
        Recorder recorder = new Recorder();
        scope.seed(Recorder.class, recorder);

        scope.get(ChildLifecycle.class);
        scope.close();

        assertEquals(
                List.of("parent:start", "child:start", "child:stop", "parent:stop"),
                recorder.events()
        );
    }

    public static class CloseableOnly implements AutoCloseable {
        static int closeCalls = 0;

        public CloseableOnly() {
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    @Test
    void autoCloseableCloseIsRegisteredWithoutAnnotation() {
        CloseableOnly.closeCalls = 0;
        Scope<RootScope> scope = new Scope<>(new RootScope());

        scope.get(CloseableOnly.class);
        scope.close();

        assertEquals(1, CloseableOnly.closeCalls);
    }

    public static class AnnotatedClose implements AutoCloseable {
        static int preDestroyCalls = 0;
        static int closeCalls = 0;

        public AnnotatedClose() {
        }

        @PreDestroy
        private void stop() {
            preDestroyCalls++;
        }

        @Override
        @PreDestroy
        public void close() {
            closeCalls++;
        }
    }

    @Test
    void annotatedCloseIsNotRegisteredTwice() {
        AnnotatedClose.preDestroyCalls = 0;
        AnnotatedClose.closeCalls = 0;
        Scope<RootScope> scope = new Scope<>(new RootScope());

        scope.get(AnnotatedClose.class);
        scope.close();

        assertEquals(1, AnnotatedClose.preDestroyCalls);
        assertEquals(1, AnnotatedClose.closeCalls);
    }

    public static class CompletionStageDestroyed {
        public CompletionStageDestroyed() {
        }

        @PreDestroy
        private java.util.concurrent.CompletionStage<Void> stopAsync() {
            return null;
        }
    }

    @Test
    void preDestroyRejectsCompletionStage() {
        Scope<RootScope> scope = new Scope<>(new RootScope());

        assertThrows(UnsupportedInjectionException.class, () -> scope.get(CompletionStageDestroyed.class));
    }

    public static class FailingDestroyed {
        static int successfulCalls = 0;

        public FailingDestroyed() {
        }

        @PreDestroy
        private void fail() {
            throw new IllegalStateException("boom");
        }

        @PreDestroy
        private void succeed() {
            successfulCalls++;
        }
    }

    @Test
    void failingDisposerDoesNotStopOtherDisposers() {
        FailingDestroyed.successfulCalls = 0;
        Scope<RootScope> scope = new Scope<>(new RootScope());
        scope.get(FailingDestroyed.class);

        ScopeException failure = assertThrows(
                ScopeException.class,
                scope::close
        );

        assertEquals(1, FailingDestroyed.successfulCalls);
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void seededValuesAreNotClosedAutomatically() {
        CloseableOnly.closeCalls = 0;
        Scope<RootScope> scope = new Scope<>(new RootScope());

        scope.seed(CloseableOnly.class, new CloseableOnly());
        scope.close();

        assertEquals(0, CloseableOnly.closeCalls);
    }

    record ChildScope(String name) {}

    @Test
    void ownedChildrenCloseInReverseAttachmentOrder() {
        Scope<RootScope> root = new Scope<>(new RootScope());
        Recorder recorder = new Recorder();

        Scope<ChildScope> first = new Scope<>(new ChildScope("first"));
        first.seed(Recorder.class, recorder);
        first.get(ChildResource.class);
        first.ownedBy(root);

        Scope<ChildScope> second = new Scope<>(new ChildScope("second"));
        second.seed(Recorder.class, recorder);
        second.get(ChildResource.class);
        second.ownedBy(root);

        root.close();

        assertEquals(List.of("second", "first"), recorder.events());
    }

    public static class ChildResource {
        private final ChildScope scope;
        private final Recorder recorder;

        public ChildResource(ChildScope scope, Recorder recorder) {
            this.scope = scope;
            this.recorder = recorder;
        }

        @PreDestroy
        private void stop() {
            recorder.add(scope.name());
        }
    }
}
