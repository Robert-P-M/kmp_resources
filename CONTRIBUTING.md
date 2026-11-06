# Contributing to KMP Resources

Thanks for your interest! This repository contains the **KMP Resources IDE plugin** (IntelliJ IDEA / Android Studio)
and — from 2.0 on — the **KMP Resources Gradle plugin** plus a shared `core` library.

## Branches

| Branch      | Purpose                                                                   |
|-------------|---------------------------------------------------------------------------|
| `feature/*`, `fix/*`, `chore/*` | Your work. Branch off `staging`.                        |
| `staging`   | Integration branch. Every PR targets `staging`. Pre-releases (`-beta.N`) are published from here. |
| `main`      | Released code only. Updated by the maintainer via PR from `staging`; releases are published automatically. |

**Never open a PR against `main`.**

## Commits & PR titles

We use [Conventional Commits](https://www.conventionalcommits.org/): `feat(ide): …`, `fix(gradle): …`, `chore(ci): …`.
Scopes: `core`, `gradle`, `ide`, `ci`, `docs`, `deps`. The PR title is checked automatically and becomes the squash commit.

## Local setup

- JDK 21
- `./gradlew check` – unit + platform tests, coverage
- `./gradlew runIde` – start a sandbox IDE with the plugin (run config *Run Plugin*)
- `./gradlew verifyPlugin` – JetBrains Plugin Verifier

## IDE plugin rules (non-negotiable)

- No blocking work on the EDT. Use `readAction {}` / `smartReadAction {}` and `writeCommandAction {}` from coroutines.
- Never swallow `ProcessCanceledException` / `CancellationException`.
- Prefer indexes and `CachedValue`s with narrow modification trackers over project-wide PSI scans.
- Services get their `CoroutineScope` injected via the constructor.

## Reporting issues

Use the issue forms. Crash reports created from the IDE's error dialog are labelled automatically.
