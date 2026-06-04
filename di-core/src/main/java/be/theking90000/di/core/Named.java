package be.theking90000.di.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifies an injected constructor parameter by name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Named {
    /**
     * Returns the qualifier name.
     *
     * @return qualifier name
     */
    String value();
}
