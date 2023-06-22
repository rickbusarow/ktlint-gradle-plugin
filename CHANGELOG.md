# Changelog

## 0.1.8-SNAPSHOT (unreleased)
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
