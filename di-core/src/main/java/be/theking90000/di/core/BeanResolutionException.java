package be.theking90000.di.core;

/**
 * Base exception for failures while resolving, selecting, or creating beans.
 */
@SuppressWarnings("serial")
public class BeanResolutionException extends DiException {
    /**
     * Creates a bean resolution exception.
     *
     * @param message detail message
     */
    public BeanResolutionException(String message) {
        super(message);
    }

    /**
     * Creates a bean resolution exception from a cause.
     *
     * @param cause underlying cause
     */
    public BeanResolutionException(Throwable cause) {
        super(cause);
    }
}
