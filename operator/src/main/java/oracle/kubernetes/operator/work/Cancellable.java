// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

/** An interface for an asynchronous invocation that can be canceled. */
public interface Cancellable {

  /** Cancels the active invocation. */
  void cancel();
}
