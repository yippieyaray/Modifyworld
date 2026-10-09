# Development and release details

<!-- Added on 2026-10-08: technical details moved out of the operator README. -->

The [README](README.md) describes installation, permissions and configuration.
This file records implementation and build details for contributors.

## Compile target and permission providers

Paper API `26.2.build.130-stable` is pinned in [pom.xml](pom.xml). The compile
target is not proof of live compatibility. See [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/).

With `op-bypass: false`, operators need an effective permission assignment for
normal actions. Modifyworld checks Bukkit `isPermissionSet` before accepting an
OP's `hasPermission` result. LuckPerms resolves assignments from users, groups,
wildcards and contexts, but excludes its implicit OP fallback from `isPermissionSet`.
With `op-bypass: true`, OPs bypass these checks, including explicit denials.
Neither setting clears WorldGuard cancellations or Minecraft admission checks.
Unknown inventory actions still default to allowed when unassigned.
Other permission providers must implement Bukkit's assignment checks correctly.

## Migration implementation

### Version ordering and migration steps

- Configuration and language migrations share `config-version` in `config.yml`.
- Parse `<major>.<minor>.<patch>[-BETA.<beta>]` numerically. A final release sorts
  after every beta of the same version.
- The existing legacy and diagnostic migrations have a fixed threshold of
  `2.0.0-BETA.6`. Missing or older markers run them; equal or newer markers skip them.
- Add future migrations with their own fixed thresholds in ascending order.
  Each step operates on the previous result and is saved before the next starts.
  Never repeat earlier migrations inside a new step.
- After successful loading, advance the marker to the running release. Preserve
  higher markers on downgrades. Fresh configurations use the running version.
- Version-only updates preserve other settings and create no backup.

### Language files and backups

- Each migration declares the standard languages it affects. Existing files receive
  missing diagnostic entries without replacing custom texts; absent standard files
  are installed from bundled resources.
- When migration is skipped, missing texts use the in-memory fallback.
- Diagnostic migration never creates `own.yml`. Legacy custom `messages` may create
  it; preserve conflicting existing files and require manual merging.
- Before changing language content, save the original as `<language>.yml.bak`,
  then `.bak.1`, etc. Configuration content changes use `config.yml.bak` similarly.

### Logging and failure handling

- Log configuration/language migration work at INFO.
- Emit `Configuration migration <from> -> <to> completed.` only after successful
  validation and saving. Fresh installations and version-only updates emit no
  migration messages.
- Invalid configuration or language data stops plugin startup.

## Build tools and test runners

Use JDK 25 and Maven (development testing used Maven 3.9.11):

```sh
java -version
mvn -version
mvn clean verify
```

For persistent user-local tools, `./build.sh` runs the same clean build and prints
Java/Maven versions first. Its defaults are JDK 25.0.4.1+1 (macOS bundle) and
Maven 3.9.11 under `~/.local/share/minecraft-devtools/`. Set
`MODIFYWORLD_JAVA_HOME` and `MODIFYWORLD_MAVEN_HOME` to use other installation
paths, including non-macOS JDK layouts. `MODIFYWORLD_TOOLS_DIR` overrides the
common tools directory, shared with other Minecraft plugin projects. Setup and
troubleshooting details are included in `build.sh`. The script does not download tools or change shell settings;
missing tools produce an error. Maven normally caches dependencies in
`~/.m2/repository/`, outside temporary storage.

With dependencies already cached, run `./build.sh -B -o clean verify` offline.
Explicit arguments replace the default `-B clean verify` arguments. The script
sets Java only for its own process and Maven children. A sandbox still needs write
permission to the Maven cache when dependencies must be downloaded.

Expected artifacts are `target/Modifyworld.jar` and `target/Modifyworld-2.0.0-BETA.7.zip`.
The first build downloads the Paper API and build/test dependencies. The API is
provided by the server and is not bundled into the plugin.

Build validation uses automated tests with mocked server services and selected
real event classes. Run `mvn verify` to execute them and package the beta.
Surefire loads Mockito as a Java agent when the test JVM starts; dynamic agent
loading is disabled. The agent uses the configured local Maven repository and
the same Mockito version as the test dependency. No runtime attachment is needed.
Run tests through Maven to apply this configuration; IDE-native test runners need
the equivalent JVM agent option. Mockito remains test-only and is not in the plugin JAR.

## Release tags and packages

GitHub release tags may append minimum-server metadata, for example
`v2.0.0-BETA.8+paper.min.26.2` or `v2.0.0-BETA.8+paper.min.26.2.build.132`. The updater compares this minimum numerically with
`Server.getMinecraftVersion()` before comparing plugin versions. Tags without
`+` retain fallback eligibility. Unknown or malformed metadata is skipped.
Everything from `+` onward is omitted from update version displays. Keep Maven
and plugin versions plain; append metadata only to the GitHub tag.
An optional build suffix requires at least that Paper build when Minecraft versions
are equal. Read it via `ServerBuildInfo.buildInfo().buildNumber()`. Higher Minecraft
versions satisfy the minimum regardless of their build number. If the build is
unknown, skip build-constrained releases at the same Minecraft version.
The minimum does not establish compatibility with all future server versions.
GitHub supplies version information; update notifications link to Hangar.

Publish the source ZIP alongside the standalone JAR and use a release tag matching the
source used for the build. Private server configurations are not included.
