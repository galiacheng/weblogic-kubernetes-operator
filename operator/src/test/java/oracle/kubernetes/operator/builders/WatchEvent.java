// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;

/**
 * This class is the test equivalent of the Watch.Response type which is returned by a Watch object.
 * It may be converted to JSON to test HTTP response handler, or to a Watch.Response object in order
 * to simulate the Watch behavior without HTTP.
 */
public class WatchEvent<T> {
  @SerializedName("type")
  private final String type;

  @SerializedName("object")
  private final T object;

  private final V1Status status;

  private WatchEvent(String type, T object) {
    this.type = type;
    this.object = object;
    this.status = null;
  }

  private WatchEvent(V1Status status) {
    this.type = "ERROR";
    this.object = null;
    this.status = status;
  }

  public static <S> WatchEvent<S> createAddedEvent(S object) {
    return new WatchEvent<>("ADDED", object);
  }

  public static <S> WatchEvent<S> createModifiedEvent(S object) {
    return new WatchEvent<>("MODIFIED", object);
  }

  public static <S> WatchEvent<S> createDeletedEvent(S object) {
    return new WatchEvent<>("DELETED", object);
  }

  public static <S> WatchEvent<S> createBookmarkEvent(S object) {
    return new WatchEvent<>("BOOKMARK", object);
  }

  public static <S> WatchEvent<S> createErrorEventWithoutStatus() {
    return new WatchEvent<>(null);
  }

  public static <S> WatchEvent<S> createErrorEvent(int statusCode) {
    return new WatchEvent<>(new V1Status().code(statusCode).message("Oops"));
  }

  public static <S> WatchEvent<S> createErrorEvent(int statusCode, String resourceVersion) {
    return new WatchEvent<>(
        new V1Status().code(statusCode).message(createMessageWithResourceVersion(resourceVersion)));
  }

  private static String createMessageWithResourceVersion(String resourceVersion) {
    return String.format("Something wrong: continue from (%s)", resourceVersion);
  }

  /**
   * Convert watch event to response.
   * @return watch response
   */
  public Watch.Response<T> toWatchResponse() {
    try {
      if (type.equals("ERROR")) {
        return toErrorWatchResponse();
      } else {
        return toUpdateWatchResponse();
      }
    } catch (NoSuchMethodException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      throw new RuntimeException("Unable to convert to Watch.Response", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Watch.Response<T> toErrorWatchResponse()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
          InstantiationException {
    @SuppressWarnings("rawtypes")
    Constructor<Watch.Response> constructor =
        Watch.Response.class.getDeclaredConstructor(String.class, V1Status.class);
    constructor.setAccessible(true);
    return (Watch.Response<T>) constructor.newInstance(type, status);
  }

  @SuppressWarnings("unchecked")
  private Watch.Response<T> toUpdateWatchResponse()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
          InstantiationException {
    @SuppressWarnings("rawtypes")
    Constructor<Watch.Response> constructor =
        Watch.Response.class.getDeclaredConstructor(String.class, Object.class);
    constructor.setAccessible(true);
    return (Watch.Response<T>) constructor.newInstance(type, object);
  }
}
