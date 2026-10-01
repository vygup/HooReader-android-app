<!--
Sync Impact Report
- Version change: 1.0.0 → 1.1.0
- Modified principles: none
- Added sections: none
- Removed sections: none
- Follow-up TODOs: Ratification date is unknown.
-->

# HooReader Constitution

## Core Principles

### I. Android-Native Delivery

HooReader MUST be delivered as an Android application. Product behavior, navigation, lifecycle
handling, and storage MUST respect Android platform conventions and supported Android API levels.
Platform-independent code is allowed only when it does not compromise this requirement. This keeps
the reader predictable for Android users and maintainable in its target environment.

### II. Kotlin and Jetpack Compose

Production application code MUST use Kotlin. User interfaces MUST be implemented with Jetpack
Compose; introducing XML view layouts or another UI framework requires a documented constitution
amendment. State exposed to Compose MUST have a clear owner and be safe across recomposition and
Android lifecycle changes.

### III. Reader Experience Is the Primary Product

Every feature MUST support reading books: discovering a catalog, acquiring content, managing the
library, or presenting readable text. Reading progress, bookmarks, and user reading preferences
MUST be persisted locally and restored after an ordinary application restart. Non-reader features
require an explicit product rationale.

### IV. First-Class OPDS Integration

OPDS catalogs MUST be usable from inside the application, without requiring an external browser or
reader to complete normal catalog discovery and acquisition. The client MUST handle catalog
navigation, authentication where supported, and clearly report network, parsing, and authorization
failures without silently losing the user's library state.

### V. Secrets Never Enter Source Control

Tokens, passwords, API keys, and equivalent credentials MUST NOT be committed to source code,
resources, test fixtures, logs, or documentation. Build-time secrets MUST be supplied through
environment variables or other approved secret-injection mechanisms; runtime credentials MUST use
Android secure storage. Repositories MUST provide only redacted examples and variable names.

## Platform & Security Constraints

Network access MUST use encrypted transport unless a documented, time-limited compatibility
exception is approved. The app MUST request only Android permissions required for a user-visible
feature. OPDS credentials and access tokens MUST be scoped to their catalog, excluded from backups
when supported by the platform, and cleared when the user removes the associated account or catalog.

## Development Workflow

Changes MUST be specified before implementation when they alter user-visible behavior, catalog
protocol behavior, storage, or security. Each commit MUST implement exactly one task; it MUST NOT
combine unrelated tasks, refactors, or formatting-only changes. Reviews MUST verify Kotlin and
Compose compliance, secure secret handling, and failure behavior for OPDS interactions. Automated
tests MUST cover business logic and critical persistence paths; protocol changes MUST include
representative OPDS success and failure cases. A release candidate MUST be validated on an Android
device or emulator.

## Governance

This constitution supersedes conflicting project practices. Any amendment MUST document its
rationale, affected workflows, and migration consequences, then be approved with the change that
adopts it. Versions follow semantic versioning: MAJOR for incompatible governance changes, MINOR
for new or materially expanded rules, and PATCH for clarifications. Every specification, plan,
task list, review, and release check MUST assess compliance; exceptions require a documented,
time-limited approval.

**Version**: 1.1.0 | **Ratified**: TODO(RATIFICATION_DATE): original adoption date was not provided | **Last Amended**: 2026-10-01
