package be.theking90000.di.core;

/**
 * Thrown when an operation is attempted on a scope that is not open.
 */
public class StateException extends CycleException {
    /**
     * Creates a state exception.
     *
     * @param message detail message
     */
    public StateException(String message) {
        super(message);
    }
}
