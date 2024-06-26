// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.backend;

import java.util.ArrayList;
import java.util.List;

/** VersionUtils contains utilities for managing the versions of the WebLogic operator REST api. */
public class VersionUtils {

  private static final String LATEST = "latest";
  private static final String V1 = "v1";
  private static final List<String> versions = new ArrayList<>();

  static {
    getVersions().add(V1);
  }

  private VersionUtils() {
    // hide implicit public constructor
  }

  /**
   * Get the supported versions of the WebLogic operator REST api.
   *
   * @return a List of version names.
   */
  public static List<String> getVersions() {
    return versions;
  }

  /**
   * Get the un-aliased name of a version of the WebLogic operator REST api.
   *
   * @param version - the potentially aliased name of the api.
   * @return - the un-aliased name of the api.
   */
  public static String getVersion(String version) {
    validateVersion(version);
    return LATEST.equals(version) ? getLatest() : version;
  }

  /**
   * Determines whether a version exists.
   *
   * @param version - the version's name (can be aliased).
   * @return whether not the version exists.
   */
  public static boolean isVersion(String version) {
    return LATEST.equals(version) || versions.contains(version);
  }

  /**
   * Gets the lifecycle of a version.
   *
   * @param version - the version's name (can be aliased). The caller is responsible for calling
   *     isVersion first and should not call this method if the version does not exist.
   * @return the version's lifecycle (either 'active' or 'deprecated')
   */
  public static String getLifecycle(String version) {
    return (isLatest(getVersion(version))) ? "active" : "deprecated";
  }

  /**
   * Get whether a version is the latest version of the WebLogic operator REST api.
   *
   * @param version - the version's name (can be aliased). The caller is responsible for calling
   *     isVersion first and should not call this method if the version does
   * @return whether this is the latest version of the WebLogic operator REST api.
   */
  public static boolean isLatest(String version) {
    return getLatest().equals(getVersion(version));
  }

  private static String getLatest() {
    return getVersions().get(0);
  }

  private static void validateVersion(String version) {
    if (!isVersion(version)) {
      throw new AssertionError("Invalid version: " + version);
    }
  }
}
