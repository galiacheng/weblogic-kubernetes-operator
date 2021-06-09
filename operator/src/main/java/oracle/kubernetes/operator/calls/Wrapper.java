// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import okhttp3.Call;
import oracle.kubernetes.operator.work.Cancellable;

/** A wrapper for an OKHttp call to isolate its own callers. */
public class Wrapper implements Cancellable {

  private final Call underlyingCall;

  public Wrapper(Call underlyingCall) {
    this.underlyingCall = underlyingCall;
  }

  @Override
  public void cancel() {
    underlyingCall.cancel();
  }
}
