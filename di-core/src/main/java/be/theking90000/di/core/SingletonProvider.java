package be.theking90000.di.core;

/**
 * Provider wrapper that caches the first non-null value returned by a delegate.
 *
 * @param <T> provided value type
 */
class SingletonProvider<T> implements Provider<T> {
    private T instance = null;
    private final Provider<T> provider;

    /**
     * Creates a singleton wrapper for a provider.
     *
     * @param provider delegate provider used to create the first value
     */
    protected SingletonProvider(Provider<T> provider) {
        this.provider = provider;
    }

    /**
     * Returns the cached value, creating it through the delegate when needed.
     *
     * @return cached or newly-created value
     */
    @Override
    public T get() {
        if (instance != null)
            return instance;

        return (instance = provider.get());
    }

    /**
     * Returns a debug representation of this provider.
     *
     * @return debug representation
     */
    @Override
    public String toString() {
        return "SingletonProvider[provider=" + provider + ",instance=" + instance + "]";
    }
}
