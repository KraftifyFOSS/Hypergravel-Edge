package pdx.dev.hypergravel.api.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Extension {

    String id();

    String name() default "";

    String version() default "0.0.0";

    String description() default "";

    String[] authors() default {};

    String[] depends() default {};

    String[] softDepends() default {};
}
