package be.theking90000.di.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-argument lifecycle method to call when the owning {@link Scope}
 * closes.
 *
 * <p>Pre-destroy methods may return {@code void} or {@link Void}. Methods
 * declared on subclasses run before methods declared on superclasses during
 * cleanup.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PreDestroy {
}
