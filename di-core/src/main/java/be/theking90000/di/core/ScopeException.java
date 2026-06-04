package be.theking90000.di.core;

/**
 * Base runtime exception for scope and injection failures.
 */
public class ScopeException extends RuntimeException {
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
