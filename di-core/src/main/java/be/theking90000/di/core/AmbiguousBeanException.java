package be.theking90000.di.core;

/**
 * Thrown when a lookup resolves more than one provider where exactly one is required.
 */
public class AmbiguousBeanException extends BeanResolutionException {
    /**
     * Creates an ambiguous-bean exception.
     *
     * @param message detail message
     */
    public AmbiguousBeanException(String message) {
        super(message);
    }
}
