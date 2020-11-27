#! /bin/sh -x
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

setUp() {
  DISALLOW_CD=
}

testRestoreFailsOnCd() {
  DISALLOW_CD="/"
  result=$(restoreDomainConfig)

  assertEquals "should have failed to cd to /" '1' "${result}"
}

cd() {
  if [ $1 = $DISALLOW_CD ]; then
    return 1
  fi
}

# shellcheck source=src/main/resources/scripts/startServer.sh
. ${SCRIPTPATH}/startServer.sh

# shellcheck source=target/classes/shunit/shunit2
. ${SHUNIT2_PATH}