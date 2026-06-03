package be.theking90000.di.core;

public class InstantiationException extends ScopeException {

    public InstantiationException(String message) {
        super(message);
    }

    public InstantiationException(Throwable thr) {
        super(thr);
    }
    
}
