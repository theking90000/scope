package be.theking90000.di.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Provider group used when a lookup can resolve zero, one, or many providers.
 *
 * @param <T> provided value type
 */
public class MultiProvider<T> implements Provider<Iterable<Provider<T>>> {
    private final Set<Provider<T>> providers = new HashSet<>();

    /**
     * Creates an empty provider group.
     */
    protected MultiProvider() {}

    /**
     * Adds a provider to this group.
     *
     * @param provider provider to add
     */
    protected void addProvider(Provider<T> provider) {
        providers.add(provider);
    }

    /**
     * Adds every provider from another group to this group.
     *
     * @param multiProvider provider group to flatten into this group
     */
    protected void addProvider(MultiProvider<T> multiProvider) {
        for (Provider<T> provider : multiProvider.providers) {
            providers.add(provider);
        }
    }

    /**
     * Converts this group to a single provider.
     *
     * @return the only provider in this group
     * @throws NoSuchBeanException if the group is empty
     * @throws AmbiguousBeanException if the group contains multiple providers
     */
    protected Provider<T> toSingleProvider() {
        if (isEmpty())
            throw new NoSuchBeanException("No provider found");

        if (!isSingle()) 
            throw new AmbiguousBeanException("Multiple providers found for this bean");

        return get().iterator().next();
    }

    /**
     * Returns the providers in this group.
     *
     * @return iterable of providers
     */
    @Override
    public Iterable<Provider<T>> get() {
        return providers;
    }

    /**
     * Returns whether this group has no providers.
     *
     * @return true when no providers are present
     */
    public boolean isEmpty() {
        return this.providers.isEmpty();
    }

    /**
     * Returns whether this group has exactly one provider.
     *
     * @return true when exactly one provider is present
     */
    public boolean isSingle() {
        return this.providers.size() == 1;
    }

    /**
     * Returns a debug representation of this provider group.
     *
     * @return debug representation
     */
    @Override
    public String toString() {
        return "MultiProvider[providers=" + providers + "]";
    }
}
