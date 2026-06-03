package be.theking90000.di.core;

public class NoSuchBeanException extends ScopeException {
    NoSuchBeanException(String message) {
        super(message);
    }
}
