package com.particlesdevs.photoncamera.settings.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a non-persistent button for a public static, no-argument void method
 * on a class in TunableRegistry. Actions run on a worker thread and are never
 * executed by preference injection, import, or resetting tunable defaults.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TunableAction {
    String title();
    String description() default "";
    String category() default "General";
    String confirmation() default "";
    String successMessage() default "Action completed";
    String failureMessage() default "Action failed";
}
