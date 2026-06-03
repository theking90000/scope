package be.theking90000.di.core;

class SingletonProvider<T> implements Provider<T> {
    private T object = null;
    private final Provider<T> provider;

    protected SingletonProvider(Provider<T> provider) {
        this.provider = provider;
    }

    public T get() {
        if (object != null)
            return object;

        return (object = provider.get());
    }
}
