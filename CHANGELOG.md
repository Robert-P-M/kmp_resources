# Changelog

## [1.0.0]

### Added

- **KMP Resources Table Editor:** A dedicated, clean UI to view, filter, and inline-edit all your strings, plurals, and
  string-arrays.
- **Tool Window & Diagnostics:** A new tool window displaying all modules, highlighting unused keys, and detecting
  missing translations across your project.
- **Safe Key Renaming:** Rename a resource key in the editor and instantly refactor all Kotlin usages (`Res.string.*`)
  across your module, including imports.
- **Real-time Inspections:** - **Format Argument Linter:** Detects missing or mismatched format arguments (like `%1$s`
  or `%d`) in Compose Multiplatform code with real-time IDE highlighting.
    - **Duplicate Key Detector:** Prevents build errors by highlighting duplicate resource keys within the same XML
      file.
- **"Create Resource" Quick Fix:** Type a non-existent key in Kotlin (e.g., `Res.string.new_key`), press Alt+Enter, and
  instantly generate the XML tag and trigger a Gradle sync.
- **Locale Management:** Easily add new translation locales to your project via a searchable popup (including flag
  emojis and country names).
- **Quick Documentation:** Hover over any KMP resource in your Kotlin code to see its actual value, type, and click
  through available translations directly in the popup.