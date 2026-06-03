package be.theking90000.di.core;

import java.util.HashSet;
import java.util.Set;

public class MultiProvider<T> implements Provider<Iterable<Provider<T>>> {
    private final Set<Provider<T>> providers = new HashSet<>();

    protected MultiProvider() {}
    
    protected void addProvider(Provider<T> provider) {
        providers.add(provider);
    }

    protected void addProvider(MultiProvider<T> mp) {
        // Flatten
        for (Provider<T> provider : mp.providers) {
            providers.add(provider);
        }
    }

    protected Provider<T> toSingleProvider() {
        if (isEmpty())
            throw new NoSuchBeanException("No provider found");
        
        if (!isSingle()) 
            throw new AmbiguousException("Multiple Providers found for this bean");

        return get().iterator().next();
    }

    @Override
    public Iterable<Provider<T>> get() {
        return providers;
    }

    public boolean isEmpty() {
        return this.providers.isEmpty();
    }

    public boolean isSingle() {
        return this.providers.size() == 1;
    }

    @Override
    public String toString() {
        return "MultiProvider[providers="+providers+"]";
    }
}
