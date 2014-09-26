#!/bin/sh

VERSION=`sw_vers -productVersion | cut -f 1,2 -d "."`
CC=clang

if [[ -z "$JAVA_HOME" ]]; then
    JAVA_HOME=`/usr/libexec/java_home`
fi

INCLUDE="-I ${JAVA_HOME}/include -I ${JAVA_HOME}/include/darwin"
FRAMEWORK="-framework Cocoa"
SRC="native/main.m"
DST="src/main/resources/io/github/appbundler/JavaAppLauncher"
SDK="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX${VERSION}.sdk"
ARCH="x86_64"
MIN_MAC_OSX_VERSION="10.7"

$CC -o $DST $FRAMEWORK $INCLUDE -arch $ARCH -isysroot $SDK -mmacosx-version-min=$MIN_MAC_OSX_VERSION $SRC
