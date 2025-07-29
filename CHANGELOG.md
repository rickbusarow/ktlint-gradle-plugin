# Changelog

## 0.2.3-SNAPSHOT (unreleased)

## [0.2.2] - 2024-01-03

### 🧰 Maintenance

- kotlin 1.9.22
- ktlint 1.1.0

**Full Changelog**: https://github.com/rickbusarow/ktlint-gradle-plugin/compare/0.2.1...0.2.2

## [0.2.1] - 2023-11-09

### Added

- Support a user-defined ktlint version. ish... in https://github.com/rickbusarow/ktlint-gradle-plugin/pull/155
  - Only `0.48.x` and `1.0.0`+ are currently supported, due to breaking changes in the ktlint binaries.

  ```kotlin
  // build.gradle[.kts]
  plugins {
    id("com.rickbusarow.ktlint")
  }

  ktlint {
    ktlintVersion.set("0.48.2")
  }
  ```

### Fixed

- make the ktlint tasks configuration-cache friendly in https://github.com/rickbusarow/ktlint-gradle-plugin/pull/149

### 🧰 Maintenance

- kotlin 1.9.10 in https://github.com/rickbusarow/ktlint-gradle-plugin/pull/153
- updated to [KtLint 1.0.1](https://github.com/pinterest/ktlint/releases/tag/1.0.1) in https://github.com/rickbusarow/ktlint-gradle-plugin/pull/117
- adds the `GitHubReleasePlugin` convention in https://github.com/rickbusarow/ktlint-gradle-plugin/pull/128
- use kgx in build logic in https://github.com/rickbusarow/ktlint-gradle-plugin/pull/129

**Full Changelog**: https://github.com/rickbusarow/ktlint-gradle-plugin/compare/0.1.8...1.2.0

## [0.2.0] - 2023-11-09

Skip this release and use [0.2.1]

## [0.1.8] - 2023-06-29

- updated to [KtLint 0.50.0](https://github.com/pinterest/ktlint/releases/tag/0.50.0)

**Full Changelog**: https://github.com/RBusarow/ktlint-gradle-plugin/compare/0.1.7...0.1.8

## [0.1.7] - 2023-06-22

### Fixed

- Filter out generated files by excluding anything from `target.buildDir`, instead of
  using `minus(target.buildDir.files)`. The latter can sometimes lead to `FileNotFoundException`s if
  running other tasks concurrently.

## [0.1.6] - 2023-06-17

### Fixed

- Tasks will now `await()` their worker before completing their action. This considerably reduces
  memory usage.

## [0.1.5] - 2023-06-13

### Fixed

- don't use coroutines from inside the ktlint worker
  in https://github.com/RBusarow/ktlint-gradle-plugin/pull/74
- make format tasks cacheable in https://github.com/RBusarow/ktlint-gradle-plugin/pull/75
- only register sourceSet-specific tasks after KGP is applied
  in https://github.com/RBusarow/ktlint-gradle-plugin/pull/76

### 🧰 Maintenance

- split up CI jobs in https://github.com/RBusarow/ktlint-gradle-plugin/pull/71

**Full Changelog**: https://github.com/RBusarow/ktlint-gradle-plugin/compare/0.1.4...0.1.5

## [0.1.4] - 2023-06-09

### Fixed

- `ktlintCheckGradleScripts` and `ktlintFormatGradleScripts` will no longer check files in included
  builds

## [0.1.3] - 2023-06-09

### Fixed

- `ktlintCheckGradleScripts` and `ktlintFormatGradleScripts` will no longer check generated script
  files in `build` directories

## [0.1.2] - 2023-06-08

### Fixed

- `ktlintCheck`, `ktlintFormat`, `ktlintCheckGradleScripts`, and `ktlintFormatGradleScripts` are now
  registered regardless of whether the target project has a Kotlin plugin applied
- `ktlintCheckGradleScripts` and `ktlintFormatGradleScripts` will no longer have the entire target
  project directory as their input

## [0.1.1] - 2023-06-04

### 🧰 Maintenance

- Format scripts in https://github.com/RBusarow/ktlint-gradle-plugin/pull/56

### Other Changes

- linkify the "col" portion of a file link output
  in https://github.com/RBusarow/ktlint-gradle-plugin/pull/57

**Full Changelog**: https://github.com/RBusarow/ktlint-gradle-plugin/compare/0.1.0...0.1.1

## [0.1.0] - 2023-05-30

Hello World

[0.1.0]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.0
[0.1.1]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.1
[0.1.2]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.2
[0.1.3]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.3
[0.1.4]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.4
[0.1.5]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.5
[0.1.6]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.6
[0.1.7]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.7
[0.1.8]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.1.8
[0.2.0]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.2.0
[0.2.1]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.2.1
[0.2.2]: https://github.com/rbusarow/ktlint-gradle-plugin/releases/tag/0.2.2
