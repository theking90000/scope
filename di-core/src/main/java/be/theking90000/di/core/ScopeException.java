package be.theking90000.di.core;

/**
 * Base exception for scope graph and lifecycle failures.
 */
public class ScopeException extends DiException {
    /**
     * Creates a scope exception.
     *
     * @param message detail message
     */
    public ScopeException(String message) {
        super(message);
    }

    /**
     * Creates a scope exception from a cause.
     *
     * @param thr underlying cause
     */
    public ScopeException(Throwable thr) {
        super(thr);
    }
}
