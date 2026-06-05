package be.theking90000.di.core;

/**
 * Cleanup callback run by {@link Scope#close()}.
 */
@FunctionalInterface
interface Disposer {
    /**
     * Runs this cleanup callback.
     *
     * @throws Exception if cleanup fails
     */
    void dispose() throws Exception;
}
