package be.theking90000.di.core;

/**
 * Thrown when constructor injection cannot instantiate a value.
 */
public class InstantiationException extends ScopeException {

    /**
     * Creates an instantiation exception.
     *
     * @param message detail message
     */
    public InstantiationException(String message) {
        super(message);
    }

    /**
     * Creates an instantiation exception from a reflection failure.
     *
     * @param thr underlying cause
     */
    public InstantiationException(Throwable thr) {
        super(thr);
    }
}
