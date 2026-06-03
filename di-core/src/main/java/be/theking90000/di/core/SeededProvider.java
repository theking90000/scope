package be.theking90000.di.core;

public class SeededProvider<T> implements Provider<T> {
    
    private final T seed;

    protected SeededProvider(T seed) {
        this.seed = seed;
    }

    @Override
    public T get() {
        return this.seed;
    }

}
