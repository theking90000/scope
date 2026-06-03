package be.theking90000.di.core;

public class FactoryProvider<T> implements Provider<T> {
    
    public FactoryProvider(Key<T> key, Scope<?> owner) {}

    @Override
    public T get() {
        return null; // Should create object and inject its content
    }
}
