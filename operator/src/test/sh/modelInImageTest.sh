#!/usr/bin/env bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

setUp() {
  DISALLOW=
  PWD=/no/where/special
  DOMAIN_HOME=/domain/home

  export TEST_CONFIGMAP_ROOT=/tmp/test/introspector

  rm -fR ${TEST_CONFIGMAP_ROOT}
  rm -fR ${TEST_CONFIGMAP_ROOT}_1
  rm -fR ${TEST_CONFIGMAP_ROOT}_2
  mkdir -p $TEST_CONFIGMAP_ROOT
  echo "<ignored>" > $TEST_CONFIGMAP_ROOT/domainzip.secure
  echo "<ignored>" > $TEST_CONFIGMAP_ROOT/primordial_domainzip.secure
}

testIndexRangeWhenRangeFileMissing() {
  actual=$(getIndexRange $TEST_CONFIGMAP_ROOT "domainzip.secure")
  expected="0 0"

  assertEquals "$expected" "$actual"
}

testIndexRangeWhenRangeFilePresent() {
  echo "0 2" > $TEST_CONFIGMAP_ROOT/domainzip.secure.range
  actual=$(getIndexRange $TEST_CONFIGMAP_ROOT "domainzip.secure")
  expected="0 2"

  assertEquals "$expected" "$actual"
}

testBuildConfigMapMultipleElements() {
  contents=$(buildConfigMapElements $TEST_CONFIGMAP_ROOT "domain.secure" 0 2)
  expect="$TEST_CONFIGMAP_ROOT/domain.secure ${TEST_CONFIGMAP_ROOT}_1/domain.secure ${TEST_CONFIGMAP_ROOT}_2/domain.secure"

  assertEquals "$expect" "$contents"
}

testBuildConfigMapOneElementAtZero() {
  contents=$(buildConfigMapElements $TEST_CONFIGMAP_ROOT "domain.secure" 0 0)
  expect="$TEST_CONFIGMAP_ROOT/domain.secure"

  assertEquals "$expect" "$contents"
}

testBuildConfigMapOneElementAfterZero() {
  contents=$(buildConfigMapElements $TEST_CONFIGMAP_ROOT "domain.secure" 1 1)
  expect="${TEST_CONFIGMAP_ROOT}_1/domain.secure"

  assertEquals "$expect" "$contents"
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

testOnRestoreDomainConfig_whenNoIndexesDefinedCatSingleFile() {
  echo -n "abc" > $TEST_CONFIGMAP_ROOT/domainzip.secure

  restoreDomainConfig

  expected="abc"
  actual="$(cat /tmp/domain.secure)"
  assertEquals "$expected" "$actual"
}

testOnRestoreDomainConfig_whenIndexesDefinedCatMultipleFiles() {
  mkdir ${TEST_CONFIGMAP_ROOT}_1
  mkdir ${TEST_CONFIGMAP_ROOT}_2
  echo "0 2" > $TEST_CONFIGMAP_ROOT/domainzip.secure.range
  echo -n "abc" > $TEST_CONFIGMAP_ROOT/domainzip.secure
  echo -n "def" > ${TEST_CONFIGMAP_ROOT}_1/domainzip.secure
  echo -n "ghi" > ${TEST_CONFIGMAP_ROOT}_2/domainzip.secure

  restoreDomainConfig

  expected="abcdefghi"
  actual="$(cat /tmp/domain.secure)"
  assertEquals "$expected" "$actual"
}

testOnRestoreDomainConfig_base64DecodeZip() {
  rm /tmp/domain.tar.gz

  restoreDomainConfig

  contents="$(cat /tmp/domain.tar.gz)"
  assertEquals "/tmp/domain.secure" $contents
}

testOnRestoreDomainConfig_unTarDomain() {
  restoreDomainConfig

  assertEquals "TAR command arguments" "-xzf /tmp/domain.tar.gz" "$TAR_ARGS"
}

testOnRestoreDomainConfig_makeScriptsExecutable() {
  restoreDomainConfig

  assertEquals "CD command arguments" "+x ${DOMAIN_HOME}/bin/*.sh ${DOMAIN_HOME}/*.sh" "$CHMOD_ARGS"
}

testOnRestorePrimordialDomain_useRootDirectory() {
  restorePrimordialDomain

  assertEquals "should be at '/'" "/" "$PWD"
}

testOnRestorePrimordialDomain_base64DecodeZip() {
  rm /tmp/domain.tar.gz

  restorePrimordialDomain

  contents="$(cat /tmp/domain.tar.gz)"
  assertEquals "/tmp/domain.secure" $contents
}

testOnRestoreDomainConfig_whenNoIndexesDefinedCatSingleFile() {
  echo -n "abc" > $TEST_CONFIGMAP_ROOT/primordial_domainzip.secure

  restorePrimordialDomain

  expected="abc"
  actual="$(cat /tmp/domain.secure)"
  assertEquals "$expected" "$actual"
}

testOnRestorePrimordialDomain_unTarDomain() {
  restorePrimordialDomain

  assertEquals "TAR command arguments" "-xzf /tmp/domain.tar.gz" "$TAR_ARGS"
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

source() {
  if [ "$DISALLOW" = "SOURCE" ]; then
    return 1
  else
    SOURCE_ARGS="$*"
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