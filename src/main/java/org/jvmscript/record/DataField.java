package org.jvmscript.record;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataField {
	int id();
	String name() default "";
	boolean output() default true;
	boolean input() default true;
	String dateFormat() default "yyyyMMdd";
}



