package be.theking90000.di.core;

public class StateException extends CycleException {
    public StateException(String message) {
        super(message);
    }
}
