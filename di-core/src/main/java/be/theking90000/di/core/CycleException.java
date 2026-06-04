package be.theking90000.di.core;

/**
 * Thrown when an operation would create an invalid cycle in the scope graph.
 */
public class CycleException extends ScopeException {
    /**
     * Creates a cycle exception.
     *
     * @param message detail message
     */
    public CycleException(String message) {
        super(message);
    }
}
