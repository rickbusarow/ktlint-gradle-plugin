# Changelog

## 0.1.5-SNAPSHOT (unreleased)

## [0.1.4] - 2023-06-09

### Fixed

- `ktlintCheckGradleScripts` and `ktlintFormatGradleScripts` will no longer check files in included builds

## [0.1.3] - 2023-06-09

### Fixed

- `ktlintCheckGradleScripts` and `ktlintFormatGradleScripts` will no longer check generated script files in `build` directories

## [0.1.2] - 2023-06-08

### Fixed

- `ktlintCheck`, `ktlintFormat`, `ktlintCheckGradleScripts`, and `ktlintFormatGradleScripts` are now registered regardless of whether the target project has a Kotlin plugin applied
- `ktlintCheckGradleScripts` and `ktlintFormatGradleScripts` will no longer have the entire target project directory as their input

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
