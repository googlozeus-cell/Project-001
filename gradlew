#!/bin/sh
set -e

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME="$DIR"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    mkdir -p "$APP_HOME/gradle/wrapper"
    WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "$WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "ERROR: curl or wget is required to bootstrap gradle-wrapper.jar" >&2
        exit 1
    fi
fi

exec "$JAVACMD" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
