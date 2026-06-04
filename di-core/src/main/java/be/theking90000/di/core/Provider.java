package be.theking90000.di.core;

/**
 * Supplies values to scopes and injected constructor parameters.
 *
 * @param <T> provided value type
 */
interface Provider<T> {
    /**
     * Returns a value from this provider.
     *
     * @return provided value
     */
    T get();
}
