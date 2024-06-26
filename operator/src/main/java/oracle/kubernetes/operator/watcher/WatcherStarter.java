// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.util.concurrent.ThreadFactory;

@FunctionalInterface
public interface WatcherStarter {

  Thread startWatcher(ThreadFactory factory, Runnable watch);
}
