#!/bin/bash

#
# Deploy SNAPSHOT source jar, javadoc jar, and jar file to bintray
#

JDK="oraclejdk8"

if [ "$TRAVIS_JDK_VERSION" != "$JDK" ]; then
    echo "Skipping snapshot deployment: wrong JDK. Expected '$JDK' but was '$TRAVIS_JDK_VERSION'."
else
    echo "Deploying snapshot..."
    ./gradlew build bintrayUpload -PuploadRepo=snapshot
    echo "Snapshot deployed!"
fi