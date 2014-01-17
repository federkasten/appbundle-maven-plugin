#!/bin/sh

CC=clang
INCLUDE="-I ${JAVA_HOME}/include -I ${JAVA_HOME}/include/darwin"
FRAMEWORK="-framework Cocoa"
SRC="native/main.m"
DST="src/main/resources/io/github/appbundler/JavaAppLauncher"
SDK="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.8.sdk"
ARCH="x86_64"
MIN_MAC_OSX_VERSION="10.7"

$CC -o $DST $FRAMEWORK $INCLUDE -arch $ARCH -isysroot $SDK -mmacosx-version-min=$MIN_MAC_OSX_VERSION $SRC
