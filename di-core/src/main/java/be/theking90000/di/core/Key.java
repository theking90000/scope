package be.theking90000.di.core;

/**
 * Identifies a bean by type and optional qualifier.
 *
 * @param type bean type
 * @param qualifier optional qualifier, or null for an unqualified key
 * @param <T> bean value type
 */
public record Key<T>(Class<T> type, Object qualifier) {

    /**
     * Creates an unqualified key for a type.
     *
     * @param type bean type
     * @param <T> bean value type
     * @return unqualified key
     */
    public static <T> Key<T> of(Class<T> type) {
        return new Key<>(type, null);
    }

    /**
     * Creates a qualified key for a type.
     *
     * @param type bean type
     * @param qualifier qualifier used to distinguish providers of the same type
     * @param <T> bean value type
     * @return qualified key
     */
    public static <T> Key<T> of(Class<T> type, Object qualifier) {
        return new Key<>(type, qualifier);
    }

    /**
     * Returns a compact debug representation of this key.
     *
     * @return type name with the qualifier when present
     */
    @Override
    public String toString() {
        return qualifier == null ? type.getSimpleName() : type.getSimpleName() + "@" + qualifier;
    }
}
