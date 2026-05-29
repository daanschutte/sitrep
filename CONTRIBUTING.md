# Contributing

## Code style

Formatting is enforced by Spotless with google-java-format. Run before committing:

```bash
./mvnw spotless:apply
```

CI will fail on formatting violations.

## Branching

Branch from `main`. Name branches `<author>/<short-description>`, e.g. `daanschutte/add-squadron-endpoint`.

## Commits

One logical change per commit. Write the commit message in the imperative: `add Squadron entity`, not `added Squadron entity`.

## Pull requests

- All CI checks must pass
- At least one reviewer
- Update `docs/PLAN.md` if the change completes or starts a phase deliverable
