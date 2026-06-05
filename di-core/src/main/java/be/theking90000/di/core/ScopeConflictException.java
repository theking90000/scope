package be.theking90000.di.core;

/**
 * Thrown when a scope attachment conflicts with an existing open scope.
 */
@SuppressWarnings("serial")
public class ScopeConflictException extends ScopeException {
    /**
     * Creates a scope conflict exception.
     *
     * @param message detail message
     */
    public ScopeConflictException(String message) {
        super(message);
    }
}
