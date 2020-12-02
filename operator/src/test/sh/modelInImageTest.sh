#! /bin/sh -x
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

setUp() {
  DISALLOW=
  PWD=/no/where/special
  DOMAIN_HOME=/domain/home
}

testRestoreDomainConfig_failsIfUnableToCDToRoot() {
  DISALLOW="CD"

  restoreDomainConfig

  assertEquals "should have failed to cd to /" '1' "$?"
}

testRestoreDomainConfig_failsIfUnableToDecodeDomainZip() {
  DISALLOW="BASE64"

  restoreDomainConfig

  assertEquals "should have failed to run decode domainzip" '1' "$?"
}

testRestoreDomainConfig_failsIfUnableToUnTarDomain() {
  DISALLOW="TAR"

  restoreDomainConfig

  assertEquals "should have failed to run tar" '1' "$?"
}

testOnRestoreDomainConfig_useRootDirectory() {
  restoreDomainConfig

  assertEquals "should be at '/'" "/" "$PWD"
}

testOnRestoreDomainConfig_base64DecodeZip() {
  rm /tmp/domain.tar.gz

  restoreDomainConfig

  contents="$(cat /tmp/domain.tar.gz)"
  assertEquals "/weblogic-operator/introspector/domainzip.secure" $contents
}

testOnRestoreDomainConfig_unTarDomain() {
  restoreDomainConfig

  assertEquals "TAR command arguments" "-xzf /tmp/domain.tar.gz" "$TAR_ARGS"
}

testOnRestoreDomainConfig_makeScriptsExecutable() {
  restoreDomainConfig

  assertEquals "CD command arguments" "+x ${DOMAIN_HOME}/bin/*.sh ${DOMAIN_HOME}/*.sh" "$CHMOD_ARGS"
}

######################### Mocks for the tests ###############

# simulates the shell 'cd' command. Will fail on CD to forbidden location, or set PWD
# otherwise
cd() {
  if [ "$DISALLOW" = "CD" ]; then
    return 1
  else
    PWD=$1
  fi
}

base64() {
  if [ "$DISALLOW" = "BASE64" ]; then
    return 1
  elif [ "$1" != "-d" ]; then
    return 1
  else
    echo "$2"
  fi
}

tar() {
  if [ "$DISALLOW" = "TAR" ]; then
    return 1
  else
    TAR_ARGS="$*"
  fi
}

chmod() {
  CHMOD_ARGS="$*"
}

# shellcheck source=src/main/resources/scripts/modelInImage.sh
. ${SCRIPTPATH}/modelInImage.sh

# shellcheck source=target/classes/shunit/shunit2
. ${SHUNIT2_PATH}