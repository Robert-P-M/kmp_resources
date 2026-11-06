## What & why
<!-- Short description. Link the issue: Closes #123 -->

## Component
- [ ] core
- [ ] gradle-plugin
- [ ] ide-plugin
- [ ] ci / docs

## Checklist
- [ ] PR title follows [Conventional Commits](https://www.conventionalcommits.org/) (`feat(ide): …`, `fix(gradle): …`)
- [ ] Targets `staging` (never `main` directly)
- [ ] Tests added / updated, `./gradlew check` is green
- [ ] No blocking work on the EDT, read/write actions via coroutine APIs (IDE plugin)
- [ ] Docs / CHANGELOG updated if user-facing
