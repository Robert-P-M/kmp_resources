# Changelog

## [Unreleased]

## [1.1.1]

### Fixed

- **Locale Creation:** Fixed an issue where adding a new locale to an Android native module incorrectly duplicated non-string resource files (like `colors.xml` or `splash.xml`) into the new localized `values` directory. The plugin now strictly creates only the necessary `strings.xml` file.

## [1.1.0]

### Added

- **Advanced Locale Management:** You can now safely **Edit** (rename) and **Remove** translation locales directly from the Editor Toolbar or Tool Window.
- **Strict BCP 47 Validation:** The plugin now strictly enforces BCP 47 language tags (e.g., `zh-Hant`, `de-AT`) and automatically maps them to the correct Android folder formats (`values-b+zh+Hant`, `values-de-rAT`).
- **Android Native Prep:** A massive under-the-hood architecture overhaul. The plugin now dynamically detects your project structure, paving the way for full native Android (`res/values`) support alongside Compose Multiplatform (`composeResources`).

### Fixed

- **IDE Startup Performance:** Optimized the Tool Window initialization to ensure zero-overhead during IDE startup.
- **API Compliance:** Resolved several Plugin Verifier warnings by migrating to modern IntelliJ Platform APIs (`ReadAction.compute`, `GotoDeclarationHandlerBase`).
- **Indexing:** Fixed an issue where the internal resource cache would not invalidate correctly upon system changes.
- **Hardcoded Paths:** Completely eliminated hardcoded path assumptions, making inspections and quick-fixes significantly more robust.
-

## [1.0.0]

### Added

- **KMP Resources Table Editor:** A dedicated UI to view, filter, and inline-edit strings, plurals, and string-arrays side-by-side. Includes **Go to Declaration (Cmd+B)** support to jump from Kotlin code directly into the editor.
- **Smart Locale Management:** Add new translation locales via a searchable popup (with flag emojis). Automatically creates `values-***` directories and XML files across all project modules.
- **Tool Window & Diagnostics:** A project-wide overview displaying all modules, highlighting **unused keys**, and detecting **missing translations** in real-time.
- **Safe Key Renaming:** Integrated refactoring that updates all Kotlin usages (`Res.string.*`) and handles necessary imports when a key is renamed in the editor.
- **Real-time Inspections:**
  - **Format Argument Linter:** Detects missing or mismatched format arguments (like `%1$s` or `%d`) in Compose Multiplatform code with IDE highlighting.
  - **Duplicate Key Detector:** Prevents build errors by flagging duplicate resource keys within the same XML file.
- **"Create Resource" Quick Fix:** Create missing resources on the fly. Type a non-existent key in Kotlin, hit **Alt+Enter**, and instantly generate the XML tag, trigger a Gradle sync, and add imports.
- **Quick Documentation:** Hover over resource references in Kotlin to see values, types, and navigate through available translations.

[Unreleased]: https://github.com/Robert-P-M/kmp_resources/compare/1.1.1...HEAD
[1.1.1]: https://github.com/Robert-P-M/kmp_resources/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/Robert-P-M/kmp_resources/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/Robert-P-M/kmp_resources/commits/1.0.0
