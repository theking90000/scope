package be.theking90000.di.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scanned lifecycle hooks for one injectable type.
 */
final class LifecycleHooks {
    private enum HookKind {
        POST_CONSTRUCT,
        PRE_DESTROY
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * An adapted lifecycle method ready for fast invocation.
     *
     * @param handle method handle adapted to {@code (Object)Object}
     * @param expectsNullReturn true when the method declares a {@code Void} return that must be null
     * @param description method description preserved for error messages
     */
    private record Hook(MethodHandle handle, boolean expectsNullReturn, String description) {}

    private final List<Hook> postConstruct;
    private final List<Hook> preDestroy;
    private final boolean autoCloseable;
    private final boolean closeAlreadyAnnotated;

    private LifecycleHooks(
            List<Hook> postConstruct,
            List<Hook> preDestroy,
            boolean autoCloseable,
            boolean closeAlreadyAnnotated
    ) {
        this.postConstruct = postConstruct;
        this.preDestroy = preDestroy;
        this.autoCloseable = autoCloseable;
        this.closeAlreadyAnnotated = closeAlreadyAnnotated;
    }

    static LifecycleHooks of(Class<?> type) {
        List<Class<?>> hierarchy = hierarchy(type);
        List<Hook> postConstruct = new ArrayList<>();
        List<Hook> preDestroy = new ArrayList<>();
        boolean closeAlreadyAnnotated = false;

        for (Class<?> current : hierarchy) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    validate(method, HookKind.POST_CONSTRUCT);
                    makeAccessible(method);
                    postConstruct.add(toHook(method));
                }

                if (method.isAnnotationPresent(PreDestroy.class)) {
                    validate(method, HookKind.PRE_DESTROY);
                    makeAccessible(method);
                    preDestroy.add(toHook(method));
                    if (isCloseMethod(method)) {
                        closeAlreadyAnnotated = true;
                    }
                }
            }
        }

        return new LifecycleHooks(
                List.copyOf(postConstruct),
                List.copyOf(preDestroy),
                AutoCloseable.class.isAssignableFrom(type),
                closeAlreadyAnnotated
        );
    }

    void postConstruct(Object instance) {
        for (Hook hook : postConstruct) {
            invokePostConstruct(hook, instance);
        }
    }

    List<Disposer> disposers(Object instance) {
        List<Disposer> disposers = new ArrayList<>();

        if (autoCloseable && !closeAlreadyAnnotated) {
            disposers.add(() -> ((AutoCloseable) instance).close());
        }

        for (Hook hook : preDestroy) {
            disposers.add(() -> invokePreDestroy(hook, instance));
        }

        return disposers;
    }

    private static Hook toHook(Method method) {
        try {
            MethodHandle handle = LOOKUP.unreflect(method)
                    .asType(MethodType.methodType(Object.class, Object.class));
            return new Hook(handle, method.getReturnType() == Void.class, method.toString());
        } catch (IllegalAccessException e) {
            throw new UnsupportedInjectionException("Cannot access lifecycle method " + method);
        }
    }

    private static List<Class<?>> hierarchy(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy);
        return hierarchy;
    }

    private static void validate(Method method, HookKind hook) {
        if (method.getParameterCount() != 0) {
            throw new UnsupportedInjectionException(
                    hook + " method " + method + " must not declare parameters"
            );
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return;
        }

        throw new UnsupportedInjectionException(
                hook + " method " + method + " has unsupported return type " + returnType.getTypeName()
        );
    }

    private static void makeAccessible(Method method) {
        if (!method.trySetAccessible()) {
            throw new UnsupportedInjectionException("Cannot access lifecycle method " + method);
        }
    }

    private static boolean isCloseMethod(Method method) {
        return method.getName().equals("close") && method.getParameterCount() == 0;
    }

    private static void invokePostConstruct(Hook hook, Object instance) {
        Object result;
        try {
            result = (Object) hook.handle().invokeExact(instance);
        } catch (Throwable t) {
            throw new BeanCreationException(t);
        }

        if (hook.expectsNullReturn() && result != null) {
            throw new BeanCreationException(hook.description() + " must return null");
        }
    }

    private static void invokePreDestroy(Hook hook, Object instance) {
        Object result;
        try {
            result = (Object) hook.handle().invokeExact(instance);
        } catch (Throwable t) {
            throw new ScopeException(t);
        }

        if (hook.expectsNullReturn() && result != null) {
            throw new ScopeException(hook.description() + " must return null");
        }
    }
}
