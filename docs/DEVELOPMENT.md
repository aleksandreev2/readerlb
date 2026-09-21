# Development workflow

## Branch model

ReaderLB uses a simple trunk-oriented workflow:

- `main` is the default and releasable branch;
- all normal work starts from the latest `main`;
- feature/fix/docs/chore branches are short-lived;
- changes return to `main` through pull requests.

The old long-running MVP branch exists only as project history. New development should not continue from it after the initial merge.

## Branch names

- `feat/<short-name>`
- `fix/<short-name>`
- `docs/<short-name>`
- `chore/<short-name>`

## Pull request gates

Before merge:

1. unit tests are green;
2. Android tests compile;
3. lint is green;
4. debug and optimized APKs build;
5. signing identity check passes;
6. size budget passes.

For storage, update or signing changes, add a manual smoke-test note.

## Releases

Stable releases are tag-driven and come only from a reviewed commit that is already in `main`.

The release tag must match `versionName`, for example `v1.0.0`.

Test artifacts from ordinary CI are not stable releases and must not be advertised by the in-app updater.

## Versioning

Before 1.0, ReaderLB uses `0.x.y`.

After 1.0:

- patch: bug/security/device compatibility fixes;
- minor: backwards-compatible features;
- major: incompatible storage/update behavior.

## Main protection

Recommended GitHub settings for `main`:

- require pull requests before merging;
- require the Android CI status check;
- block force pushes;
- block branch deletion;
- dismiss stale approvals when code changes if external contributors are active.

Repository-admin settings are configured in GitHub itself; they are not stored in this repository.
