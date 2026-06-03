package be.theking90000.di.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * This class represent a Lifetime for beans
 * Each beans belongs to exactly one Scope
 * However Scope can belongs to multiple parents Scope
 * Each scope contains a relation to its parents, a parent can Own a scope,
 * meaning when the parent scope is destroyed, the descending scope is also destroyed
 * 
 * A scope should always have at least one owner scope except for the root Scope, which doesn't have.
 * Multiple owner are possible, meaning if *any* owner is destroyed the descending scope is destroyed.
 * 
 * Scope can also have visibility to parents.
 * 
 * There is thread way of access:
 * 
 * 1. Scope (owns=true, visible=true) -> parent : the scope is a direct descendant of the parent scope, it can read from it and is destroyed() automatically when parent is destroy. At least one edge required.
 * 
 * 2. Scope (owns=true, visible=false) -> parent: the scope cannot read parent (isolated) but its lifetime is tied to parent lifetime
 * 
 * 3. Scope (owns=false, visible=true) -> parent : the scope can read parent beans BUT does not go out of scope when parent does. ! this can lead to problems, since a bean could reference destroyed beans in another scope, to investigate.
 * 
 * 4. Scope (owns=false, visible=false) : the scope is not linked to parent scope and cannot access it-> why keep it?
 * 
 * Note for any AI agent reading : should i keep these four ways, or it would make more sens to always set "owns=True" and only keep visible?
 */
public final class Scope<C> implements AutoCloseable {
    enum State { OPEN, CLOSING, CLOSED }

    enum Collect { NEAREST, DEEP }

    private final C context;

    private final Map<Object, Scope<?>> ownedScopes = new HashMap<>();
    private final Set<Edge<?>> parents = new HashSet<>();

    // Provider cache
    private final Map<Key<?>, MultiProvider<?>> providers = new HashMap<>();

    private State state = State.OPEN;

    @SuppressWarnings("unchecked")
    public Scope(C context) {
        this.context = context;
        
        // Rendre le context accessible
        seed(Key.of((Class<C>) context.getClass()), context);
        seed(Key.of(Scope.class), this);
    };

    public <V> Scope<C> provide(Key<V> key, Provider<V> provider) {
        checkOpen();
            
        local(key).addProvider(provider);

        return this;
    }

    public <V> Scope<C> seed(Key<V> key, V value) {
        provide(key, new SeededProvider<V>(value));

        return this;
    }

    public Scope<C> attach(Scope<?> parent, boolean owns, boolean visible) {
        checkOpen();

        if(reaches(parent, this)) {
            throw new CycleException(this.context + " -> " + parent.context + " fermerait une boucle");
        }

        if (owns) {
            Scope<?> current = parent.ownedScopes.get(this.context);
            if (current != null && current.state == State.OPEN)
                throw new ScopeException("Existing scope cannot be replaced");

            parent.ownedScopes.put(this.context, this);
        }

        parents.add(new Edge<>(parent, owns, visible));

        return this;
    }

    public Scope<C> ownedBy(Scope<?> p) throws ScopeException { return attach(p, true,  true); }
    public Scope<C> weakRef(Scope<?> p) throws ScopeException { return attach(p, false, true); }

    private static boolean reaches(Scope<?> from, Scope<?> target) {
        if (from == target) return true;
        for (Edge<?> e : from.parents) if (reaches(e.parent(), target)) return true;
        return false;
    }

    public <V> Provider<V> provider(Key<V> key) {
        MultiProvider<V> mp = providers(key, Collect.NEAREST);

        if (mp.isEmpty()) {
            mp.addProvider(implicitHost(key).local(key));
        }

        // Throw NoSuchBeanException et Ambiguous si N==0 ou N>1;
        return mp.toSingleProvider();
    }

    public <V> MultiProvider<V> providers(Key<V> key, Collect mode) {
        Set<Scope<?>> hosts = new LinkedHashSet<>();
        // Resolve via BFS all scopes providing this key
        collectAll(key, mode, new HashSet<>(), hosts);
        
        MultiProvider<V> mp = new MultiProvider<>();

        for (Scope<?> h : hosts) {
            mp.addProvider(h.local(key));
        }

        return mp;
    }

    @SuppressWarnings("unchecked")
    private <V> MultiProvider<V> local(Key<V> key) {
        MultiProvider<V> mp = (MultiProvider<V>) this.providers
                .computeIfAbsent(key, (k) -> new MultiProvider<>());

        return mp;
    }

    // Returns a list of all the Scope<?> that provides this key
    // Two collection mode avalaible:
    // - Collect.NEAREST : returns the nearest scope providing this key on each branch
    // - Collect.DEEP = returns all the parents scopes providing this key
    private void collectAll(Key<?> key, Collect mode, Set<Scope<?>> seen, Set<Scope<?>> hosts) {
        checkOpen();
        
        if (!seen.add(this)) return;

        boolean defines = providers.containsKey(key);
        if (defines) hosts.add(this);

        if (defines && mode == Collect.NEAREST) return;

        for (Edge<?> e : parents)
            if (e.visible() && e.parent().state == State.OPEN)
                e.parent().collectAll(key, mode, seen, hosts);
    }

    /*
     * When we don't know who own this ressources, return the potential owner
     * For now it's always the current Scope.
     */
    private Scope<?> implicitHost(Key<?> key) {
        if (!isInstantiable(key))
           throw new NoSuchBeanException(key + " : pas lié, pas instanciable");

        // Object o = null; // créer l'objet, test pas d'objet pour l'instant

        local(key).addProvider(new SeededProvider<>(null));

        return this;
    }

    public boolean isInstantiable(Key<?> key) {
        return false;
    }

    private void checkOpen() throws CycleException {
        if (state != State.OPEN) throw new StateException(toString() + " est en " + state);
    }

    @Override public void close() {
    if (state != State.OPEN) return;                 
        state = State.CLOSING;
        List<Scope<?>> owned = new ArrayList<>(ownedScopes.values());
        Collections.reverse(owned);
        for (Scope<?> c : owned) c.close();  

        owned.clear();
    
        // pas implmenté
        // while (!disposers.isEmpty()) disposers.pop().run();   // mes beans, LIFO
   
        providers.clear();
    
        for (Edge<?> e : parents) e.parent().ownedScopes.remove(this.context);   // détache de TOUS mes parents
    
        state = State.CLOSED;
    }

    @Override public String toString() { return "Scope(" + context + ")"; }

}
