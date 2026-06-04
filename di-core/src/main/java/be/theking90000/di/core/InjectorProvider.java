package be.theking90000.di.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider that builds values through an {@link Injector}.
 *
 * <p>During creation it recursively builds the providers needed by constructor
 * parameters. Newly-created providers are registered into a temporary context
 * first so lazy circular dependencies can resolve against the same provider
 * graph before the graph is committed to a {@link Scope}.</p>
 *
 * @param <T> provided value type
 */
public class InjectorProvider<T> implements Provider<T> {

    private final Injector<T> injector;
    private final List<Provider<?>> providers;

    /**
     * Creates a provider for a key and records all providers created with it.
     *
     * @param key key represented by this provider
     * @param scope scope used to resolve existing dependencies
     * @param context temporary provider graph under construction
     */
    private InjectorProvider(Key<T> key, Scope<?> scope, Map<Key<?>, Provider<?>> context) {
        this.injector = Injector.of(key.type());

        // Register before resolving parameters so Provider<T> cycles can refer back here.
        context.put(key, new SingletonProvider<>(this));

        this.providers = new ArrayList<>();

        for (Injector.InjectedKey<?> injectedKey : this.injector.getParameters()) {
            Key<?> parameterKey = injectedKey.key();

            Provider<?> provider = context.get(parameterKey);
            if (provider != null) {
                this.providers.add(provider);
                continue;
            }

            MultiProvider<?> multiProvider = scope.providers(parameterKey, Scope.Collect.NEAREST);

            if (multiProvider.isEmpty()) {
                new InjectorProvider<>(parameterKey, scope, context);
                provider = context.get(parameterKey);
            } else {
                provider = multiProvider.toSingleProvider();
            }

            this.providers.add(provider);
        }
    }

    /**
     * Creates the provider graph required to resolve a key.
     *
     * @param key root key to create
     * @param scope scope used for dependency lookup
     * @return providers created during graph construction, keyed by dependency key
     */
    protected static Map<Key<?>, Provider<?>> create(Key<?> key, Scope<?> scope) {
        Map<Key<?>, Provider<?>> context = new HashMap<>();
        new InjectorProvider<>(key, scope, context);

        return context;
    }

    /**
     * Creates or returns the scoped singleton instance for this provider.
     *
     * @return provided value
     */
    @Override
    public T get() {
        return injector.instantiate(providers);
    }

    /**
     * Returns a debug representation of this provider.
     *
     * @return debug representation
     */
    @Override
    public String toString() {
        return "InjectorProvider[injector=" + injector + "]";
    }
}
