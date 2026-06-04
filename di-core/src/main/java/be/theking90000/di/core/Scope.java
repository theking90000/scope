package be.theking90000.di.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lifetime container for beans that are resolved and cached within a scoped graph.
 *
 * <p>A scope contains local providers and can point to one or more parent scopes.
 * Parent edges have two independent flags: {@code owns} ties this scope's
 * lifetime to the parent, while {@code visible} allows bean lookup to continue
 * through that parent. Multiple owned parents are allowed; closing any owner
 * closes this scope through the owner's child list.</p>
 *
 * <p>Every scope seeds its context object and the scope itself during
 * construction, making both available for constructor injection. Automatically
 * created beans are stored as scoped singletons and are cleared when the scope
 * closes.</p>
 *
 * @param <C> the type of context object represented by this scope
 */
public class Scope<C> implements AutoCloseable {
    /**
     * Internal directed relationship from this scope to one of its parents.
     *
     * @param parent parent scope reached by this edge
     * @param owns true when the parent owns this scope's lifetime
     * @param visible true when lookups may traverse this edge
     * @param <T> parent context type
     */
    private record Edge<T>(Scope<T> parent, boolean owns, boolean visible) {}

    /**
     * Lifecycle states used to guard operations and make close idempotent.
     */
    enum State {
        /**
         * Providers can be registered and resolved.
         */
        OPEN,

        /**
         * The scope is closing its children and local resources.
         */
        CLOSING,

        /**
         * The scope has released its providers and detached from its parents.
         */
        CLOSED
    }

    /**
     * Controls how provider lookup walks visible parent scopes.
     */
    enum Collect {
        /**
         * Stops at the first scope that provides the requested key on each branch.
         */
        NEAREST,

        /**
         * Collects every visible parent scope that provides the requested key.
         */
        DEEP
    }

    private final C context;

    /**
     * Direct owned descendants keyed by their context object.
     */
    private final Map<Object, Scope<?>> ownedScopes = new HashMap<>();

    /**
     * Parent edges that define lifetime ownership and visibility.
     */
    private final Set<Edge<?>> parents = new HashSet<>();

    /**
     * Local provider cache for seeded, explicitly registered, and auto-created beans.
     */
    private final Map<Key<?>, MultiProvider<?>> providers = new HashMap<>();

    /**
     * Local cleanup callbacks executed in LIFO order during close.
     */
    private final Deque<Runnable> disposers = new ArrayDeque<>();

    private State state = State.OPEN;

    /**
     * Creates a scope for the given context and seeds the context plus this scope.
     *
     * @param context context object that identifies and describes this scope
     */
    @SuppressWarnings("unchecked")
    public Scope(C context) {
        this.context = context;

        seed(Key.of((Class<C>) context.getClass()), context);
        seed(Key.of(Scope.class), this);
    }

    /**
     * Registers a provider for an unqualified type in this scope.
     *
     * @param type provided type
     * @param provider provider to register
     * @param <V> provided value type
     * @return this scope for chaining
     * @throws StateException if this scope is not open
     */
    public <V> Scope<C> provide(Class<V> type, Provider<V> provider) {
        return provide(Key.of(type), provider);
    }

    /**
     * Registers a provider for a key in this scope.
     *
     * @param key provider key
     * @param provider provider to register
     * @param <V> provided value type
     * @return this scope for chaining
     * @throws StateException if this scope is not open
     */
    public <V> Scope<C> provide(Key<V> key, Provider<V> provider) {
        checkOpen();

        local(key).addProvider(provider);

        return this;
    }

    /**
     * Registers an already-created value for an unqualified type.
     *
     * @param type seeded value type
     * @param value value to return for this type
     * @param <V> seeded value type
     * @return this scope for chaining
     * @throws StateException if this scope is not open
     */
    public <V> Scope<C> seed(Class<V> type, V value) {
        return seed(Key.of(type), value);
    }

    /**
     * Registers an already-created value for a key.
     *
     * @param key seeded value key
     * @param value value to return for this key
     * @param <V> seeded value type
     * @return this scope for chaining
     * @throws StateException if this scope is not open
     */
    public <V> Scope<C> seed(Key<V> key, V value) {
        provide(key, new SeededProvider<>(value));

        return this;
    }

    /**
     * Attaches this scope to a parent scope.
     *
     * <p>When {@code owns} is true, the parent will close this scope when the
     * parent closes. When {@code visible} is true, lookup can continue into that
     * parent. The resulting graph must remain acyclic.</p>
     *
     * @param parent parent scope to attach
     * @param owns whether the parent owns this scope's lifetime
     * @param visible whether this scope can resolve providers from the parent
     * @return this scope for chaining
     * @throws CycleException if the edge would create a cycle
     * @throws ScopeException if this scope would replace an open owned child
     * @throws StateException if this scope is not open
     */
    public Scope<C> attach(Scope<?> parent, boolean owns, boolean visible) {
        checkOpen();

        if (reaches(parent, this)) {
            throw new CycleException(this.context + " -> " + parent.context + " would create a cycle");
        }

        if (owns) {
            Scope<?> current = parent.ownedScopes.get(this.context);
            if (current != null && current.state == State.OPEN) {
                throw new ScopeException("Existing scope cannot be replaced");
            }

            parent.ownedScopes.put(this.context, this);
        }

        parents.add(new Edge<>(parent, owns, visible));

        return this;
    }

    /**
     * Attaches this scope to a visible parent that owns its lifetime.
     *
     * @param parent visible owner scope
     * @return this scope for chaining
     * @throws ScopeException if the attachment is invalid
     */
    public Scope<C> ownedBy(Scope<?> parent) throws ScopeException {
        return attach(parent, true, true);
    }

    /**
     * Attaches this scope to a visible parent without lifetime ownership.
     *
     * @param parent visible non-owning parent
     * @return this scope for chaining
     * @throws ScopeException if the attachment is invalid
     */
    public Scope<C> weakRef(Scope<?> parent) throws ScopeException {
        return attach(parent, false, true);
    }

    /**
     * Returns whether {@code target} is reachable from {@code from} through parents.
     *
     * @param from starting scope
     * @param target scope to find
     * @return true when {@code target} is reachable
     */
    private static boolean reaches(Scope<?> from, Scope<?> target) {
        if (from == target) return true;
        for (Edge<?> edge : from.parents) {
            if (reaches(edge.parent(), target)) return true;
        }
        return false;
    }

    /**
     * Resolves a value for a key.
     *
     * @param key key to resolve
     * @param <V> resolved value type
     * @return resolved value
     * @throws NoSuchBeanException if no provider can be found or created
     * @throws AmbiguousException if more than one nearest provider matches
     */
    public <V> V get(Key<V> key) {
        return provider(key).get();
    }

    /**
     * Resolves a value for an unqualified type.
     *
     * @param type type to resolve
     * @param <V> resolved value type
     * @return resolved value
     * @throws NoSuchBeanException if no provider can be found or created
     * @throws AmbiguousException if more than one nearest provider matches
     */
    public <V> V get(Class<V> type) {
        return get(Key.of(type));
    }

    /**
     * Resolves a provider for an unqualified type.
     *
     * @param type type to resolve
     * @param <V> provided value type
     * @return provider for the requested type
     * @throws NoSuchBeanException if no provider can be found or created
     * @throws AmbiguousException if more than one nearest provider matches
     */
    public <V> Provider<V> provider(Class<V> type) {
        return provider(Key.of(type));
    }

    /**
     * Resolves a provider for a key, creating constructor-injected providers when absent.
     *
     * @param key key to resolve
     * @param <V> provided value type
     * @return provider for the requested key
     * @throws NoSuchBeanException if no provider can be found or created
     * @throws AmbiguousException if more than one nearest provider matches
     */
    public <V> Provider<V> provider(Key<V> key) {
        MultiProvider<V> multiProvider = providers(key, Collect.NEAREST);

        if (multiProvider.isEmpty()) {
            multiProvider.addProvider(create(key));
        }

        return multiProvider.toSingleProvider();
    }

    /**
     * Resolves all nearest providers for an unqualified type.
     *
     * @param type type to resolve
     * @param <V> provided value type
     * @return matching providers
     */
    public <V> MultiProvider<V> providers(Class<V> type) {
        return providers(Key.of(type));
    }

    /**
     * Resolves providers for an unqualified type using a collection mode.
     *
     * @param type type to resolve
     * @param mode parent traversal mode
     * @param <V> provided value type
     * @return matching providers
     */
    public <V> MultiProvider<V> providers(Class<V> type, Collect mode) {
        return providers(Key.of(type), mode);
    }

    /**
     * Resolves all nearest providers for a key.
     *
     * @param key key to resolve
     * @param <V> provided value type
     * @return matching providers
     */
    public <V> MultiProvider<V> providers(Key<V> key) {
        return providers(key, Collect.NEAREST);
    }

    /**
     * Resolves providers for a key using a collection mode.
     *
     * @param key key to resolve
     * @param mode parent traversal mode
     * @param <V> provided value type
     * @return matching providers
     */
    public <V> MultiProvider<V> providers(Key<V> key, Collect mode) {
        Set<Scope<?>> hosts = new LinkedHashSet<>();
        collectAll(key, mode, new HashSet<>(), hosts);

        MultiProvider<V> multiProvider = new MultiProvider<>();

        for (Scope<?> host : hosts) {
            multiProvider.addProvider(host.local(key));
        }

        return multiProvider;
    }

    /**
     * Returns the local provider group for a key, creating it when needed.
     *
     * @param key provider key
     * @param <V> provided value type
     * @return local provider group
     */
    @SuppressWarnings("unchecked")
    private <V> MultiProvider<V> local(Key<V> key) {
        MultiProvider<V> multiProvider = (MultiProvider<V>) this.providers
                .computeIfAbsent(key, (k) -> new MultiProvider<>());

        return multiProvider;
    }

    /**
     * Collects visible scopes that locally define a provider for the key.
     *
     * <p>{@link Collect#NEAREST} stops traversal on a branch as soon as a
     * matching scope is found. {@link Collect#DEEP} keeps walking through
     * visible parents after a match.</p>
     *
     * @param key provider key to find
     * @param mode parent traversal mode
     * @param seen scopes already visited in this traversal
     * @param hosts output set of scopes that provide the key
     */
    private void collectAll(Key<?> key, Collect mode, Set<Scope<?>> seen, Set<Scope<?>> hosts) {
        checkOpen();

        if (!seen.add(this)) return;

        boolean defines = providers.containsKey(key);
        if (defines) hosts.add(this);

        if (defines && mode == Collect.NEAREST) return;

        for (Edge<?> edge : parents) {
            if (edge.visible() && edge.parent().state == State.OPEN) {
                edge.parent().collectAll(key, mode, seen, hosts);
            }
        }
    }

    /**
     * Creates and registers constructor-injected providers for an unresolved key.
     *
     * <p>Providers are added only after the full dependency graph can be built,
     * which keeps partially-created graphs out of this scope when creation
     * fails.</p>
     *
     * @param key key to create
     * @param <T> provided value type
     * @return created provider for the requested key
     */
    @SuppressWarnings("unchecked")
    private <T> Provider<T> create(Key<T> key) {
        Map<Key<?>, Provider<?>> addedProviders = InjectorProvider.create(key, this);

        for (Map.Entry<Key<?>, Provider<?>> entry : addedProviders.entrySet()) {
            provide((Key<Object>) entry.getKey(), (Provider<Object>) entry.getValue());
        }

        return local(key).toSingleProvider();
    }

    /**
     * Returns whether the key can be automatically instantiated.
     *
     * <p>This method is currently a placeholder and always returns false.</p>
     *
     * @param key key to inspect
     * @return false until explicit instantiability checks are implemented
     */
    public boolean isInstantiable(Key<?> key) {
        return false;
    }

    /**
     * Ensures this scope can still accept operations.
     *
     * @throws StateException if this scope is closing or closed
     */
    private void checkOpen() throws CycleException {
        if (state != State.OPEN) throw new StateException(toString() + " is " + state);
    }

    /**
     * Closes this scope and all owned descendants.
     *
     * <p>Owned child scopes are closed first, then local disposers run in LIFO
     * order, local providers are cleared, and this scope is detached from every
     * parent owner list. Calling this method after the first close has no effect.</p>
     */
    @Override
    public void close() {
        if (state != State.OPEN) return;

        state = State.CLOSING;
        List<Scope<?>> owned = new ArrayList<>(ownedScopes.values());
        Collections.reverse(owned);
        for (Scope<?> child : owned) {
            child.close();
        }

        owned.clear();

        while (!disposers.isEmpty()) {
            disposers.pop().run();
        }

        providers.clear();

        for (Edge<?> edge : parents) {
            edge.parent().ownedScopes.remove(this.context);
        }

        state = State.CLOSED;
    }

    /**
     * Finds a visible open ancestor scope with the given context.
     *
     * @param context context object to match
     * @param <T> context type
     * @return the matching scope, or null when none is visible
     */
    @SuppressWarnings("unchecked")
    public <T> Scope<T> findParent(T context) {
        if (this.context.equals(context)) return (Scope<T>) this;
        for (Edge<?> edge : parents) {
            if (edge.visible() && edge.parent().state == State.OPEN) {
                Scope<T> scope = edge.parent().findParent(context);
                if (scope != null) return scope;
            }
        }
        return null;
    }

    /**
     * Finds an owned descendant scope with the given context.
     *
     * @param context context object to match
     * @param <T> context type
     * @return the matching owned descendant, or null when none exists
     */
    @SuppressWarnings("unchecked")
    public <T> Scope<T> getChild(T context) {
        for (Scope<?> owned : ownedScopes.values()) {

            if (owned.context.equals(context)) return (Scope<T>) owned;
            Scope<T> scope = owned.getChild(context);
            if (scope != null) return scope;
        }
        return null;
    }

    /**
     * Returns a short debug representation using the context object.
     *
     * @return debug representation of this scope
     */
    @Override
    public String toString() {
        return "Scope(" + context + ")";
    }

}
