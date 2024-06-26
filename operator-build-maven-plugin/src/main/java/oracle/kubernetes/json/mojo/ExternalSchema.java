// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

class ExternalSchema {

  private final String url;
  private final String cache;

  ExternalSchema(String url, String cache) {
    this.url = url;
    this.cache = cache;
  }

  URL getUrl() throws MalformedURLException, URISyntaxException {
    return new URI(url).toURL();
  }

  URL getCacheUrl(String baseDir) throws MalformedURLException {
    return new File(new File(baseDir), cache).toURI().toURL();
  }
}
