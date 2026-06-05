package be.theking90000.di.core;

/**
 * Thrown when dependency injection cannot instantiate a bean.
 */
@SuppressWarnings("serial")
public class BeanCreationException extends BeanResolutionException {
    /**
     * Creates a bean creation exception.
     *
     * @param message detail message
     */
    public BeanCreationException(String message) {
        super(message);
    }

    /**
     * Creates a bean creation exception from a cause.
     *
     * @param cause underlying cause
     */
    public BeanCreationException(Throwable cause) {
        super(cause);
    }
}
