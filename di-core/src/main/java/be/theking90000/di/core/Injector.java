package be.theking90000.di.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Injector<T> {

    static record InjectedKey<T>(Key<T> key, boolean isProvider) {}
    
    private final Class<T> cls;
    private final Constructor<T> constructor;

    private final List<InjectedKey<?>> parameters = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private Injector(
        Class<T> cls
    ) {
        this.cls = cls;

        Constructor<?>[] constructors = cls.getConstructors();

        if (constructors.length == 1) {
            this.constructor = (Constructor<T>) constructors[0];

            for (Parameter p : constructor.getParameters()) {
                Named named = p.getAnnotation(Named.class);
                String name = named != null ? named.value() : null;
                boolean isProvider = false;
                Class<?> type = p.getType();

                if (p.getType() == Provider.class) {
                    isProvider = true;

                    Type pp = p.getParameterizedType();

                    if (pp instanceof ParameterizedType pt) {
                        Type[] args = pt.getActualTypeArguments();

                        if (args.length != 1) {
                            throw new NoSuchBeanException(
                                cls + " Provider is missing type argument : Provider<T>"
                            );
                        }

                        Type arg = args[0];

                        if (!(arg instanceof Class<?> clazz)) {
                            throw new NoSuchBeanException(
                                cls + " Provider<T> requires T to be a concrete class"
                            );
                        }

                        type = clazz;
                    } else {
                        throw new NoSuchBeanException(
                            cls + " Provider is missing type argument : Provider<T>"
                        );
                    }
                }

                if (cls.isLocalClass() || cls.isAnonymousClass()) {
                    throw new NoSuchBeanException(
                        cls + " local/anonymous classes are not supported by DI"
                    );
                }

                if (cls.getEnclosingClass() != null && !Modifier.isStatic(cls.getModifiers())) {
                    throw new NoSuchBeanException(
                        cls + " non-static inner classes are not supported by DI"
                    );
                }
                
                parameters.add(new InjectedKey<>(Key.of(type, name), isProvider));
            }
        } else {
            throw new NoSuchBeanException(
                cls + " Doesn't have exactly one public constructor"
            );
        }
        
    }

    public Iterable<InjectedKey<?>> getParameters() {
        return parameters;
    }

    public T instanciate(List<Provider<?>> providers) {
        if (providers.size() != this.parameters.size())
            throw new InstantiationException("Missing parameters for "+ cls.getCanonicalName() +  " received " + providers.size()+ " expected "+this.parameters.size());

        Object[] parameters = new Object[this.parameters.size()];

        for (int i = 0; i < providers.size(); i++) {
            if (this.parameters.get(i).isProvider()) {
                parameters[i] = providers.get(i);
            } else {
                parameters[i] = providers.get(i).get();
            }
        }

        try {
            return this.constructor.newInstance(parameters);
        } catch (ReflectiveOperationException e) {
            throw new InstantiationException(e);
        }
    }

    public static <T> Injector<T> of(Class<T> cls) {
        return new Injector<>(cls);
    }

    @Override
    public String toString() {
        return "Injector[cls=" + cls + "]";
    }

}
