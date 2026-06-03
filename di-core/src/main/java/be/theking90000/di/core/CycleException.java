package be.theking90000.di.core;

public class CycleException extends ScopeException {
    public CycleException(String message) {
        super(message);
    }
}