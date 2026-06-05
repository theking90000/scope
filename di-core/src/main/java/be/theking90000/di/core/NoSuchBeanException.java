package be.theking90000.di.core;

/**
 * Thrown when a lookup cannot resolve or create a provider for a key.
 */
@SuppressWarnings("serial")
public class NoSuchBeanException extends BeanResolutionException {
    /**
     * Creates a missing-bean exception.
     *
     * @param message detail message
     */
    public NoSuchBeanException(String message) {
        super(message);
    }
}
