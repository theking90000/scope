package be.theking90000.di.core;

public class AmbiguousException extends ScopeException {
    AmbiguousException(String message) {
        super(message);
    }
}
