package be.theking90000.di.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InjectorProvider<T> implements Provider<T> {
    
    private final Injector<T> injector;
    private final List<Provider<?>> providers;

    private InjectorProvider(Key<T> key, Scope<?> scope, Map<Key<?>, Provider<?>> context) {
        this.injector = Injector.of(key.type());

        // Self-Register as the official provider for this Key, Avoids infinite recursion loop? : no
        // this.scope.provide(key, this);
        // Here : Decide if singleton or not?
        
        // No Singleton : new instance each time
        // context.put(key, this);

        // Singleton : unique instance within the scope
        context.put(key, new SingletonProvider<>(this));

        this.providers = new ArrayList<>();

        for (Injector.InjectedKey<?> injectedKey :  this.injector.getParameters()) {
            Key<?> params = injectedKey.key();
            // TODO: can use !injectedKey.isProvider() to check agains't infinte loop and cycles

            Provider<?> provider = context.get(params);
            if (provider != null) {
                this.providers.add(provider);
                continue;
            }

            MultiProvider<?> mp = scope.providers(params, Scope.Collect.NEAREST);

            if (mp.isEmpty()) {
                // Create with the same scope
                new InjectorProvider<>(params, scope, context);
                provider = context.get(params);
            } else {
                provider = mp.toSingleProvider();
            }            
            
            this.providers.add(provider);
        }
    }

    protected static Map<Key<?>, Provider<?>> create(Key<?> key, Scope<?> scope) {
        Map<Key<?>, Provider<?>> context = new HashMap<>();
        new InjectorProvider<>(key, scope, context);

        return context;
    }
 
    @Override
    public T get() {
       return injector.instanciate(providers);
    }

    @Override
    public String toString() {
        return "InjectorProvider[injector="+injector+"]";
    }
    

}
