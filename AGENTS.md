# Agent Instructions

Guidance for AI coding agents of any vendor working in the Gradle repository.
Human contributors should start from [CONTRIBUTING.md](CONTRIBUTING.md).

## Before you start

- Follow the [code change guidelines](CONTRIBUTING.md#code-change-guidelines) as well as the relevant topical contributing guidelines:
  - For information about how to to add suggestions to error messages, see [ErrorMessages.md](contributing/ErrorMessages.md).
  - For JavaDoc style guidelines, see [JavadocStyleGuide.md](contributing/JavadocStyleGuide.md).
  - For guidelines on nullability and related annotations, see [Nullability.md](contributing/Nullability.md).
  - For information on writing tests for Gradle, see [Testing.md](contributing/Testing.md).

## Property provenance work

- Keep provenance separate from ordinary Provider/property logic, including `DefaultMapProperty`, `AbstractCollectionProperty`, `AbstractProperty`, and `ManagedFactories`. Do not add provenance fields, bookkeeping, diagnostic branches, or observer calls to those engines.
- Keep value selection, validation, evaluation, producer dependencies, and lifecycle in the existing engines. Use diagnostic subclasses/adapters and shared provenance helpers; do not duplicate engine algorithms to produce diagnostics.
- Keep `org.gradle.api.internal.provenance` descriptor-only, dependent only on the JDK, JSpecify, and its own types. Runtime Provider/host integration belongs in adapters outside that package.
- Keep managed transport in `ProvenanceManagedFactory` and the serialization adapters. Ordinary managed factories consume ordinary value state, without provenance envelopes or checkpoint handling.
- Narrow, provenance-neutral protected engine seams are acceptable when needed by adapters. Prefer explicit forwarding in diagnostic subclasses over moving diagnostic control flow into the engines to reduce repetition.
- Before extending diagnostic coverage, read [the collection separation contract](PROPERTY_PROVENANCE_COLLECTION_HOOKS.md#separation-boundary), [the D3 transport checkpoint](PROPERTY_PROVENANCE_D3.md), [the D4 gap audit](PROPERTY_PROVENANCE_D4_RESEARCH.md), and [the D4 checkpoint](PROPERTY_PROVENANCE_D4.md). Older checkpoint documents use superseded milestone numbering.
