package org.jvmscript.record;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FixedWidthField {
	String name() default "";
	int start();
	int length();
	String dateFormat() default "yyyyMMdd";
	int scale() default 0;
}



