package be.theking90000.di.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection helper that describes how to construct a type through dependency injection.
 *
 * <p>An injector accepts classes with exactly one public constructor. Constructor
 * parameters are converted to {@link Key keys}; parameters of type
 * {@code Provider<T>} are injected lazily while all other parameters receive the
 * value returned by their provider.</p>
 *
 * @param <T> type created by this injector
 */
public class Injector<T> {

    /**
     * Dependency key requested by a constructor parameter.
     *
     * @param key key requested by the constructor
     * @param isProvider true when the parameter expects {@code Provider<T>}
     * @param <T> dependency value type
     */
    static record InjectedKey<T>(Key<T> key, boolean isProvider) {}

    private final Class<T> type;
    private final Constructor<T> constructor;

    private final List<InjectedKey<?>> parameters = new ArrayList<>();

    /**
     * Creates an injector for a class with exactly one public constructor.
     *
     * @param type class to inspect
     * @throws NoSuchBeanException if the class cannot be constructed by this injector
     */
    @SuppressWarnings("unchecked")
    private Injector(Class<T> type) {
        this.type = type;

        Constructor<?>[] constructors = type.getConstructors();

        if (constructors.length == 1) {
            this.constructor = (Constructor<T>) constructors[0];

            for (Parameter parameter : constructor.getParameters()) {
                Named named = parameter.getAnnotation(Named.class);
                String name = named != null ? named.value() : null;
                boolean isProvider = false;
                Class<?> parameterType = parameter.getType();

                if (parameter.getType() == Provider.class) {
                    isProvider = true;

                    parameterType = readType(parameter.getParameterizedType());
                }

                if (type.isLocalClass() || type.isAnonymousClass()) {
                    throw new NoSuchBeanException(
                        type + " local/anonymous classes are not supported by DI"
                    );
                }

                if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
                    throw new NoSuchBeanException(
                        type + " non-static inner classes are not supported by DI"
                    );
                }

                parameters.add(new InjectedKey<>(Key.of(parameterType, name), isProvider));
            }
        } else {
            throw new NoSuchBeanException(
                type + " does not have exactly one public constructor"
            );
        }
    }

    /**
     * Reads the concrete type argument from a parameterized constructor parameter.
     *
     * @param parameterizedType parameterized type to inspect
     * @return concrete class represented by the type argument
     * @throws NoSuchBeanException if the type argument is missing or not concrete
     */
    private Class<?> readType(Type parameterizedType) {
        if (parameterizedType instanceof ParameterizedType parameterType) {
            Type[] args = parameterType.getActualTypeArguments();

            if (args.length != 1) {
                throw new NoSuchBeanException(
                    type + " " + parameterizedType.getTypeName()
                            + " is missing type argument: " + parameterizedType.getTypeName() + "<T>"
                );
            }

            Type arg = args[0];

            if (arg instanceof ParameterizedType nestedParameterType) {
                if (nestedParameterType.getRawType() == Iterable.class) {
                    return readType(nestedParameterType);
                }                
            }

            if (!(arg instanceof Class<?> clazz)) {
                throw new NoSuchBeanException(
                    type + " " + parameterizedType.getTypeName() + "<T> requires T to be a concrete class"
                );
            }

            return clazz;
        } 

        throw new NoSuchBeanException(
            type + " " + parameterizedType.getTypeName()
                    + " is missing type argument: " + parameterizedType.getTypeName() + "<T>"
        );
    }

    /**
     * Returns the constructor dependencies in declaration order.
     *
     * @return constructor dependency keys
     */
    public Iterable<InjectedKey<?>> getParameters() {
        return parameters;
    }

    /**
     * Creates an instance from providers matching this injector's constructor parameters.
     *
     * @param providers providers in the same order as {@link #getParameters()}
     * @return newly constructed instance
     * @throws InstantiationException if the provider list is incomplete or reflection fails
     */
    public T instantiate(List<Provider<?>> providers) {
        if (providers.size() != this.parameters.size())
            throw new InstantiationException(
                    "Missing parameters for " + type.getCanonicalName()
                            + ": received " + providers.size()
                            + ", expected " + this.parameters.size()
            );

        Object[] resolvedParameters = new Object[this.parameters.size()];

        for (int i = 0; i < providers.size(); i++) {
            if (this.parameters.get(i).isProvider()) {
                resolvedParameters[i] = providers.get(i);
            } else {
                resolvedParameters[i] = providers.get(i).get();
            }
        }

        try {
            return this.constructor.newInstance(resolvedParameters);
        } catch (ReflectiveOperationException e) {
            throw new InstantiationException(e);
        }
    }

    /**
     * Creates an instance from providers matching this injector's constructor parameters.
     *
     * @param providers providers in the same order as {@link #getParameters()}
     * @return newly constructed instance
     * @deprecated use {@link #instantiate(List)}
     */
    @Deprecated
    public T instanciate(List<Provider<?>> providers) {
        return instantiate(providers);
    }

    /**
     * Creates an injector for a type.
     *
     * @param type type to inspect
     * @param <T> type created by the injector
     * @return injector for the requested type
     * @throws NoSuchBeanException if the type cannot be constructed by this injector
     */
    public static <T> Injector<T> of(Class<T> type) {
        return new Injector<>(type);
    }

    /**
     * Returns a debug representation of this injector.
     *
     * @return debug representation
     */
    @Override
    public String toString() {
        return "Injector[type=" + type + "]";
    }

}
