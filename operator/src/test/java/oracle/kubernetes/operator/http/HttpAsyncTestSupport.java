// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

import static com.meterware.simplestub.Stub.createStub;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * A class to enable unit testing of async http requests.
 */
public class HttpAsyncTestSupport {
  private static final HttpResponse<String> NOT_FOUND = createStub(HttpResponseStub.class, HTTP_NOT_FOUND);
  private static final RequestValidation[] NO_VALIDATIONS = new RequestValidation[0];
  private static final RequestHandler NO_SUCH_HANDLER = new RequestHandler(null, NOT_FOUND, NO_VALIDATIONS);

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private HttpAsyncRequestStep.FutureFactory futureFactory = this::getFuture;
  private final Map<URI, List<RequestHandler>> cannedResponses = new HashMap<>();

  /**
   * Defines the response for an async http request.
   * @param request the expected request
   * @param response the desired result
   * @param validations a (possibly empty) array of additional tests on the request
   */
  public void defineResponse(HttpRequest request, HttpResponse<String> response, RequestValidation... validations) {
    assert validations != null;
    cannedResponses.putIfAbsent(request.uri(), new ArrayList<>());
    cannedResponses.get(request.uri()).add(new RequestHandler(request, response, validations));
  }

  /**
   * Represent a function that applies a test to an HTTP request and returns true if it is valid.
   */
  @FunctionalInterface
  public interface RequestValidation {
    boolean isValid(HttpRequest request);
  }

  HttpResponse<String> getResponse(HttpRequest request) {
    return getHandler(request).getResponse();
  }

  RequestHandler getHandler(HttpRequest request) {
    return Optional.ofNullable(cannedResponses.get(request.uri()))
          .map(l -> getMatchingRequest(l, request))
          .orElse(NO_SUCH_HANDLER);
  }

  private RequestHandler getMatchingRequest(List<RequestHandler> handlers, HttpRequest request) {
    return handlers.stream().filter(h -> h.matches(request)).findFirst().orElse(null);
  }

  public CompletableFuture<HttpResponse<String>> getFuture(HttpRequest request) {
    return getHandler(request).future;
  }

  static class RequestHandler {
    private final HttpRequest request;
    private final CompletableFuture<HttpResponse<String>> future;
    private final HttpResponse<String> response;
    private final RequestValidation[] validations;

    RequestHandler(HttpRequest request, HttpResponse<String> response, RequestValidation[] validations) {
      this.request = request;
      this.validations = validations;
      this.future = new CompletableFuture<>();
      this.future.complete(response);
      this.response = response;
    }

    private HttpResponse<String> getResponse() {
      return response;
    }

    private boolean matches(HttpRequest request) {
      return isMatchingRequest(request, this.request) && isValid(request);
    }

    private boolean isValid(HttpRequest request) {
      return Arrays.stream(validations).allMatch(v -> v.isValid(request));
    }

    private boolean isMatchingRequest(HttpRequest left, HttpRequest right) {
      return left.method().equals(right.method());
    }
  }

  public Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(HttpAsyncRequestStep.class, "factory", futureFactory);
  }
}
