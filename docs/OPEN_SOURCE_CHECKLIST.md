# Open-source readiness checklist

This checklist contains the remaining repository-owner decisions and GitHub
settings that cannot be safely inferred from source code.

## Required before calling ReaderLB fully open source

- [x] Choose and add an OSI-compatible software license in a root `LICENSE`
  file (GNU GPL v3.0).
- [x] Update the README license section to name the selected license.

Publishing source without a license allows viewing/forking through GitHub, but
does not grant the normal reuse, modification and redistribution permissions
expected from an open-source project.

## Repository settings

- [ ] Protect `main` with a GitHub branch rule/ruleset.
- [ ] Require pull requests before merge.
- [ ] Require Android build and CodeQL checks.
- [ ] Block force pushes and deletion of `main`.
- [ ] Add a short repository description and useful topics such as
  `android`, `kotlin`, `epub`, `ranobelib` and `jetpack-compose`.
- [ ] Enable private vulnerability reporting if available for the repository.

These settings live in GitHub administration and are intentionally not
represented by fake configuration files in the source tree.

## Stable release channel

- [ ] Create and securely back up one permanent Android release keystore.
- [ ] Add the four `READERLB_KEYSTORE_*` GitHub Actions secrets.
- [ ] Configure `FIREBASE_SERVICE_ACCOUNT` if release push notifications are
  desired.
- [ ] Publish the first release-signed build from a tag pointing at `main`.
- [ ] Publish a second release-signed build and prove an in-place
  stable-to-stable update works on a physical device.
- [ ] Verify the push reaches an opted-in device after a stable release.

## Project presentation

- [ ] Add 2–4 current application screenshots to the README once the UI is
  considered stable enough to represent the project.
- [ ] Replace the pre-1.0 status note after the first production-ready release.

Everything else in this checklist can evolve incrementally after 1.0.