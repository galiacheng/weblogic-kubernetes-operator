#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home in docker image, and generates the domain resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The WDT sample requires that JAVA_HOME is set to a java JDK version 1.8 or greater
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#    * The kubernetes persisitent volume must already be created
#    * The kubernetes persisitent volume claim must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../../common/utility.sh
source ${scriptDir}/../../common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file -u username -p password [-k] [-e] [-v] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Output directory for the generated properties and YAML files, must be specified."
  echo "  -u Username used in building the Docker image for WebLogic domain in image."
  echo "  -p Password used in building the Docker image for WebLogic domain in image."
  echo "  -e Also create the resources in the generated YAML files, optional."
  echo "  -v Validate the existence of persistentVolumeClaim, optional."
  echo "  -k Keep what has been previously from cloned https://github.com/oracle/docker-images.git, optional. "
  echo "     If not specified, this script will always remove existing project directory and clone again."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
doValidation=false
executeIt=false
cloneIt=true
while getopts "evhki:o:u:p:" opt; do
  case $opt in
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    v) doValidation=true
    ;;
    e) executeIt=true
    ;;
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    k) cloneIt=false;
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${valuesInputFile} ]; then
  echo "${script}: -i must be specified."
  missingRequiredOption="true"
fi

if [ -z ${username} ]; then
  echo "${script}: -u must be specified."
  missingRequiredOption="true"
fi

if [ -z ${password} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ -z ${outputDir} ]; then
  echo "${script}: -o must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

#
# Function to initialize and validate the output directory
# for the generated properties and yaml files for this domain.
#
function initOutputDir {
  domainOutputDir="${outputDir}/weblogic-domains/${domainUID}"
  # Create a directory for this domain's output files
  mkdir -p ${domainOutputDir}

  removeFileIfExists ${domainOutputDir}/${valuesInputFile}
  removeFileIfExists ${domainOutputDir}/create-domain-inputs.yaml
  removeFileIfExists ${domainOutputDir}/domain.properties
  removeFileIfExists ${domainOutputDir}/domain.yaml
}

# try to execute docker to see whether docker is available
function validateDockerAvailable {
  if ! [ -x "$(command -v docker)" ]; then
    validationError "docker is not installed"
  fi
}

#
# Function to setup the environment to create domain
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateDockerAvailable
  validateKubectlAvailable

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain-inputs.yaml)."
  else
    if [ ! -f ${valuesInputFile} ]; then
      validationError "Unable to locate the input parameters file ${valuesInputFile}"
    fi
  fi

  if [ -z "${outputDir}" ]; then
    validationError "You must use the -o option to specify the name of an existing directory to store the generated properties and yaml files in."
  fi

  domainPropertiesInput="${scriptDir}/properties-template.properties"
  if [ ! -f ${domainPropertiesInput} ]; then
    validationError "The template file ${domainPropertiesInput} for creating a WebLogic domain was not found"
  fi

  dcrInput="${scriptDir}/../../common/domain-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain resource was not found"
  fi

  failIfValidationErrors

  validateCommonInputs

  validateBooleanInputParamsSpecified logHomeOnPV
  failIfValidationErrors

  initOutputDir

  if [ "${cloneIt}" = true ]; then
    getDockerSample
  fi
}

#
# Function to get the dependency docker sample
#
function getDockerSample {
  rm -rf ${scriptDir}/docker-images
  git clone https://github.com/oracle/docker-images.git ${scriptDir}/docker-images
}

#
# Function to generate the properties and yaml files for creating a domain
#
function createFiles {

  # Make sure the output directory has a copy of the inputs file.
  # The user can either pre-create the output directory, put the inputs
  # file there, and create the domain from it, or the user can put the
  # inputs file some place else and let this script create the output directory
  # (if needed) and copy the inputs file there.
  copyInputsFileToOutputDirectory ${valuesInputFile} "${domainOutputDir}/create-domain-inputs.yaml"

  domainPropertiesOutput="${domainOutputDir}/domain.properties"
  dcrOutput="${domainOutputDir}/domain.yaml"

  initializeInput

  if [ -z "${domainHomeImageBase}" ]; then
    fail "Please specify domainHomeImageBase in your input YAML"
  fi

  domainHome="/u01/oracle/user_projects/domains/${domainName}"

  # Generate the properties file that will be used when creating the weblogic domain
  echo Generating ${domainPropertiesOutput}

  cp ${domainPropertiesInput} ${domainPropertiesOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${domainPropertiesOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${domainPropertiesOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${domainPropertiesOutput}
  sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${domainPropertiesOutput}
  sed -i -e "s:%MANAGED_SERVER_NAME_BASE%:${managedServerNameBase}:g" ${domainPropertiesOutput}
  sed -i -e "s:%CONFIGURED_MANAGED_SERVER_COUNT%:${configuredManagedServerCount}:g" ${domainPropertiesOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${domainPropertiesOutput}
  sed -i -e "s:%PRODUCTION_MODE_ENABLED%:${productionModeEnabled}:g" ${domainPropertiesOutput}
  sed -i -e "s:%CLUSTER_TYPE%:${clusterType}:g" ${domainPropertiesOutput}
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${domainPropertiesOutput}
  sed -i -e "s:%T3_CHANNEL_PORT%:${t3ChannelPort}:g" ${domainPropertiesOutput}
  sed -i -e "s:%T3_PUBLIC_ADDRESS%:${t3PublicAddress}:g" ${domainPropertiesOutput}

  generateDomainYaml true
}

#
# Function to build docker image and create WebLogic domain home
#
function createDomainHome {
  dockerDir=${scriptDir}/${domainHomeImageBuildPath}
  dockerPropsDir=${dockerDir}/properties
  cp ${domainPropertiesOutput} ${dockerPropsDir}/docker-build

  # 12213-domain-home-in-image use one properties file for the credentials 
  usernameFile="${dockerPropsDir}/docker-build/domain_security.properties"
  passwordFile="${dockerPropsDir}/docker-build/domain_security.properties"
 
  # 12213-domain-home-in-image-wdt uses two properties files for the credentials 
  if [ ! -f $usernameFile ]; then
    usernameFile="${dockerPropsDir}/docker-build/adminuser.properties"
    passwordFile="${dockerPropsDir}/docker-build/adminpass.properties"
  fi
  
  sed -i -e "s|myuser|${username}|g" $usernameFile
  sed -i -e "s|mypassword1|${password}|g" $passwordFile
    
  if [ ! -z $domainHomeImageBase ]; then
    sed -i -e "s|\(FROM \).*|\1 ${domainHomeImageBase}|g" ${dockerDir}/Dockerfile
  fi

  sh ${dockerDir}/build.sh
  imageNameOrigin="`basename ${domainHomeImageBuildPath}`"

  # if use the default images, we tag it to a more generic name (without the release version numbers)
  if [ -z $image ]; then
    docker tag $imageNameOrigin:latest $imageName:latest
  fi

  if [ "$?" != "0" ]; then
    fail "Create domain ${domainName} failed."
  fi

  echo ""
  echo "Create domain ${domainName} successfully."
}

#
# Function to output to the console a summary of the work completed
#
function printSummary {

  # Get the IP address of the kubernetes cluster (into K8S_IP)
  getKubernetesClusterIP

  echo ""
  echo "Domain ${domainName} was created and will be started by the WebLogic Kubernetes Operator"
  echo ""
  if [ "${exposeAdminNodePort}" = true ]; then
    echo "Administration console access is available at http:${K8S_IP}:${adminNodePort}/console"
  fi
  if [ "${exposeAdminT3Channel}" = true ]; then
    echo "T3 access is available at t3:${K8S_IP}:${t3ChannelPort}"
  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-domain-inputs.yaml"
  echo "  ${domainPropertiesOutput}"
  echo "  ${dcrOutput}"
  echo ""
  echo "Completed"
}

# Perform the sequence of steps to create a domain
createDomain

