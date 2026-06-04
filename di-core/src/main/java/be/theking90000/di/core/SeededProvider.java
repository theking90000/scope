package be.theking90000.di.core;

/**
 * Provider that always returns a pre-existing value.
 *
 * @param <T> provided value type
 */
public class SeededProvider<T> implements Provider<T> {

    private final T seed;

    /**
     * Creates a provider for a seeded value.
     *
     * @param seed value returned by this provider
     */
    protected SeededProvider(T seed) {
        this.seed = seed;
    }

    /**
     * Returns the seeded value.
     *
     * @return seeded value
     */
    @Override
    public T get() {
        return this.seed;
    }
}
