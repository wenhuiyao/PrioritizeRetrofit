#!/bin/bash

#
# Deploy source jar, javadoc jar, and jar file to bintray
#

JDK="oraclejdk8"

if [ "$TRAVIS_JDK_VERSION" != "$JDK" ]; then
    echo "Skipping artifact deployment: wrong JDK. Expected '$JDK' but was '$TRAVIS_JDK_VERSION'."
else
    echo "Deploying artifact..."
    ./gradlew build bintrayUpload
    echo "Artifact deployed!"
fi