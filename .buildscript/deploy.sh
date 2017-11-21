#!/bin/bash

JDK="oraclejdk8"
BRANCH="master"

if [ "$TRAVIS_JDK_VERSION" != "$JDK" ]; then
    echo "Skipping snapshot deployment: wrong JDK. Expected '$JDK' but was '$TRAVIS_JDK_VERSION'."
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
    echo "Skipping snapshot deployment: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'."
else
    echo "Deploying snapshot..."
    ./gradlew build bintrayUpload
    echo "Snapshot deployed!"
fi