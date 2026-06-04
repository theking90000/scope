package be.theking90000.di.core;

/**
 * Thrown when adding a scope edge would create a cycle in the scope graph.
 */
public class ScopeCycleException extends ScopeException {
    /**
     * Creates a scope cycle exception.
     *
     * @param message detail message
     */
    public ScopeCycleException(String message) {
        super(message);
    }
}
