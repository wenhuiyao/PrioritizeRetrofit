#!/bin/bash

#
# Deploy source jar, javadoc jar, and jar file to bintray
#

JDK="oraclejdk8"
BRANCH="master"

if [ "$TRAVIS_JDK_VERSION" != "$JDK" ]; then
    echo "Skipping snapshot deployment: wrong JDK. Expected '$JDK' but was '$TRAVIS_JDK_VERSION'."
else
    echo "Deploying snapshot..."
    ./gradlew build bintrayUpload
    echo "Snapshot deployed!"
fi