// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

/** Indicates that schema should specify an additional properties element for the annotated map. */
@Retention(RetentionPolicy.RUNTIME)
@Target(FIELD)
public @interface AdditionalProperties {
}
