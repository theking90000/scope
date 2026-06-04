package be.theking90000.di.core;

/**
 * Thrown when a lookup cannot resolve or create a provider for a key.
 */
public class NoSuchBeanException extends ScopeException {
    /**
     * Creates a missing-bean exception.
     *
     * @param message detail message
     */
    NoSuchBeanException(String message) {
        super(message);
    }
}
