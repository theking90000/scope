package be.theking90000.di.core;

/**
 * Thrown when a lookup resolves more than one provider where exactly one is required.
 */
public class AmbiguousException extends ScopeException {
    /**
     * Creates an ambiguity exception.
     *
     * @param message detail message
     */
    AmbiguousException(String message) {
        super(message);
    }
}
