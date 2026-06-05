package be.theking90000.di.core;

/**
 * Thrown when an operation is attempted on a scope that is not open.
 */
@SuppressWarnings("serial")
public class ScopeStateException extends ScopeException {
    /**
     * Creates a scope state exception.
     *
     * @param message detail message
     */
    public ScopeStateException(String message) {
        super(message);
    }
}
