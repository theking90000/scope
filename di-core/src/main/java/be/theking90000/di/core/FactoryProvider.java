package be.theking90000.di.core;

/**
 * Placeholder provider for future factory-based creation.
 *
 * <p>This class is not wired into the current resolver. Its current behavior is
 * intentionally unchanged for this cleanup pass: {@link #get()} returns
 * {@code null}.</p>
 *
 * @param <T> provided value type
 */
public class FactoryProvider<T> implements Provider<T> {

    /**
     * Creates a placeholder factory provider.
     *
     * @param key key this provider would create
     * @param owner scope that would own created values
     */
    public FactoryProvider(Key<T> key, Scope<?> owner) {}

    /**
     * Returns the placeholder value.
     *
     * @return null until factory creation is implemented
     */
    @Override
    public T get() {
        return null;
    }
}
