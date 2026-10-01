#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-or-later
# Added on 2026-09-30: select persistent user-local build tools.
# Modified on 2026-10-01: use and document shared Minecraft development tools.
#
# SETUP (once per developer machine; tools are not included in Git):
# Install/extract a trusted JDK 25 and Apache Maven 3.9.11 into persistent
# user-local storage. The default layout on macOS is:
#   ~/.local/share/minecraft-devtools/jdk-25.0.4.1+1/Contents/Home/bin/java
#   ~/.local/share/minecraft-devtools/jdk-25.0.4.1+1/Contents/Home/bin/javac
#   ~/.local/share/minecraft-devtools/apache-maven-3.9.11/bin/mvn
# This directory is shared by Modifyworld and SimpleLoginMessages. Do not
# create a new per-plugin tool installation when adapting this script.
# The previous modifyworld-tools directory was renamed to minecraft-devtools.
# No downloads or installations are performed by this script.
#
# PATH AND OVERRIDES:
# Java/Maven do not need to be on the caller's PATH. This script selects them
# explicitly and sets JAVA_HOME/PATH only for itself and child processes.
# MODIFYWORLD_TOOLS_DIR overrides the common installation directory.
# MODIFYWORLD_JAVA_HOME overrides the JDK home (must contain bin/java and javac).
# MODIFYWORLD_MAVEN_HOME overrides the Maven home (must contain bin/mvn).
# Individual home overrides take precedence over the common tools directory.
# On Linux or with another JDK distribution, set MODIFYWORLD_JAVA_HOME to its
# actual JDK home; the default Contents/Home layout is specific to macOS.
# SimpleLoginMessages uses SIMPLELOGINMESSAGES_* overrides with the same shared
# default directory. When copying this script, adapt override names, not the
# shared minecraft-devtools directory name. Stale overrides still take priority.
#
# BUILD / DIAGNOSIS:
#   ./build.sh                    # prints versions, then runs -B clean verify
#   ./build.sh -B -o clean verify # offline, once dependencies are cached
#   ./build.sh -version           # tool startup check only, not a build/test
#   sh /path/to/Modifyworld/build.sh -B clean verify
# Explicit arguments replace the default Maven arguments. The script changes
# to its own project directory, so it can be called from another directory.
# Maven normally uses ~/.m2/repository; settings.xml or -Dmaven.repo.local can
# override this. Do not put shared tools or the dependency cache under /tmp.
# A first build or changed dependencies may require network/cache write access.
# Sandbox permission for those operations is separate from executable access.
# Modifyworld's pom.xml loads Mockito at test-JVM startup; no self-attach or
# sandbox escalation for dynamic attachment is required.
# A missing tool is a path/install issue, not proof that the shell cannot run it.
# A successful -version check proves tool startup only. Claim build/test success
# only after checking the actual build exit code and test results.
# Normal `mvn -B clean verify` also works with a separately configured JDK 25.
set -eu

tools_dir=${MODIFYWORLD_TOOLS_DIR:-"$HOME/.local/share/minecraft-devtools"}
build_java_home=${MODIFYWORLD_JAVA_HOME:-"$tools_dir/jdk-25.0.4.1+1/Contents/Home"}
build_maven_home=${MODIFYWORLD_MAVEN_HOME:-"$tools_dir/apache-maven-3.9.11"}

if [ ! -x "$build_java_home/bin/java" ] || [ ! -x "$build_java_home/bin/javac" ]; then
    echo "JDK not found at $build_java_home. Set MODIFYWORLD_JAVA_HOME to a JDK 25 installation." >&2
    exit 1
fi
if [ ! -x "$build_maven_home/bin/mvn" ]; then
    echo "Maven not found at $build_maven_home. Set MODIFYWORLD_MAVEN_HOME to a Maven 3.9.11 installation." >&2
    exit 1
fi

export JAVA_HOME="$build_java_home"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

"$JAVA_HOME/bin/java" -version
"$build_maven_home/bin/mvn" -version
if [ "$#" -eq 0 ]; then
    set -- -B clean verify
fi
exec "$build_maven_home/bin/mvn" "$@"
