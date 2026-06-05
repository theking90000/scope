package be.theking90000.di.core;

/**
 * Base runtime exception for all dependency injection failures.
 */
@SuppressWarnings("serial")
public class DiException extends RuntimeException {
    /**
     * Creates a dependency injection exception.
     *
     * @param message detail message
     */
    public DiException(String message) {
        super(message);
    }

    /**
     * Creates a dependency injection exception from a cause.
     *
     * @param cause underlying cause
     */
    public DiException(Throwable cause) {
        super(cause);
    }
}
