/*
 * Copyright 2026 Gradle and contributors.
 *
 * Licensed under the Creative Commons Attribution-Noncommercial-ShareAlike 4.0 International License.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://creativecommons.org/licenses/by-nc-sa/4.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

# Property provenance: D4 gap audit

Research checkpoint, 2026-09-08, against D3 commit `2cab346d789`.
The subsequent implementation is recorded in [the D4 checkpoint](PROPERTY_PROVENANCE_D4.md).
This is a source and existing-test audit, not an implementation or a new runtime test run.
Current numbering: D3 transport; D4 broader diagnostics; D5 Java/Kotlin locations;
D6 validation/rollout; D7 Groovy locations. Older documents and the D3 comment in
`PropertyProvenanceDiagnostics.mutation` use earlier numbering.

## What is already covered

Diagnostic scalar/list/set/map properties and their diagnostic snapshots report missing
required `get()` values. Selected rejected mutations report the attempted operation and
the accepted configuration. IllegalArgumentException/IllegalStateException categories
are preserved, the original exception is the direct cause, and already-reported failures
are not decorated twice. Finalized properties honestly report unknown caller attribution.
Recognized replacement-map chains already have structural update descriptors; that is
not general-purpose provenance propagation through arbitrary derived providers.

D3 preserves these checkpoints through managed isolation and configuration-cache reuse,
including missing values, snapshots, aliases, and changing task-produced values.
It does not itself extend the evaluation/reporting surface.

Evidence: `DiagnosticProperty`, `DiagnosticProvenanceSnapshot`,
`CollectionProvenanceSnapshot`, `Diagnostic{List,Set,Map}Property`,
`PropertyProvenanceDiagnostics`, and their focused unit/integration tests under
`platforms/core-configuration/model-core/src`.

## Confirmed gaps and decisions

| Area | Current implementation / gap | D4 work needed |
| --- | --- | --- |
| Direct derived reads | `AbstractMinimalProvider.map`, `filter`, `flatMap`, and both `orElse` overloads return ordinary derived providers; `ProviderInternal.zip` returns `BiProvider`. They do not expose `ProvenanceAware`. Upstream `calculateValue` does not pass through diagnostic `calculateOwnPresentValue`, so a derived provider can create its own undecorated missing-value exception. | Define descriptor-only boundary reports and implement opt-in adapters. Cover live properties, snapshots, nested chains, and transported providers. |
| Branching and multi-input providers | `flatMap` selects a provider during evaluation; `orElse` selects a fallback; `zip` combines inputs. Existing local mutation descriptors are not a record of these runtime choices. | Decide how to distinguish known source context, actual evaluated branch, and unknown/partial coverage. Never query a branch merely to explain it or label dependencies as accepted local mutations. |
| Map projections | `DefaultMapProperty.getting` and `keySet` return ordinary inner providers. An entry provider can be missing because a key is absent even when the map is present. | Add adapter-level coverage and distinguish absent entry from absent map. Do not infer which contribution supplied/overwrote a key; do not include configured keys/values in new descriptors. |
| Evaluation / transform failures | Diagnostic required-read overrides catch `MissingValueException` only. `PropertyProvenanceDiagnostics.mutation` only decorates IllegalArgumentException/IllegalStateException. Arbitrary supplier/transform exceptions and named `PropertyQueryException` wrappers have no general provenance decoration. | Define a failure taxonomy and exception attachment policy before adding catches. Distinguish immediate `replace` callback failure from later evaluation of its returned provider. Preserve original failures/causes and avoid duplicate or misleading reports. |
| Early mutation validation | Collection `add` and map `put` validate some arguments before reaching diagnostic collector hooks. Map `putAll(Provider)` checks provider type first; invalid `setFromAnyValue` can throw before delegated `set`. Null checks can throw NullPointerException, which the failure adapter does not decorate. | Audit public overloads and aliases, including add/append, put/insert, bulk variants, and dynamic assignment. Add narrowly scoped diagnostic overrides; retain validation order, exception behavior, and zero accepted occurrences for rejection. |
| Read/lifecycle surfaces | Existing reporting centers on required `get()`. Nullable/presence reads, internal `calculateValue`, execution-time calculation, and finalization take different engine paths. `AbstractProperty` deliberately leaves `beforeRead` failures outside its ordinary value-query wrapper. `TransformBackedProvider.beforeRead` rejects reads before producer completion. | Test and decide which failures receive context at each boundary. Missing nullable/presence reads must remain successful. Do not broadly wrap unsafe-read or lifecycle failures without preserving their contracts. |
| Task-facing failures | D3 integration exercises task values and dependencies, but does not establish complete provenance reporting for task input validation or producer/unsafe-read failures. | Trace task consumer error boundaries and existing exception handling before choosing an attachment point. This is a remaining investigation, not proof that all task failures currently lose context. |
| New diagnostics after transport | D3 codecs preserve objects implementing `ProvenanceAware`; ordinary derived providers currently do not implement it. | Any D4 derived adapter needs direct/serialized isolation and configuration-cache store/reuse tests, including whether provider codecs preserve its behavior as well as its checkpoint. Do not assume D3 automatically covers a new adapter. |

The relevant runtime sources are in
`platforms/core-configuration/model-core/src/main/java/org/gradle/api/internal/provider`.
Transport codecs are in
`platforms/core-configuration/core-serialization-codecs/src/main/kotlin/org/gradle/internal/serialize/codecs/core/ProviderCodecs.kt`.

## Recommended implementation order

1. Pin down diagnostic contracts with tests: direct missing `map`/`filter`, nested chains,
   map projections, and the contrast with binding a derived provider to a tracked target.
   Specify live versus captured source context and a failure-time checkpoint when supplier
   evaluation mutates its source. Start with a single-input adapter slice.
2. Define and test `flatMap`, `orElse`, and `zip` boundary semantics before extending that
   adapter. If source selection cannot be established without extra evaluation, report
   partial coverage instead of guessing or eagerly examining both branches.
3. Add transform/evaluation reporting using an explicit exception policy, including
   named/unnamed properties, failed finalization, unsafe reads, circular evaluation,
   attribution lookup failures, and already-decorated upstream failures.
4. Close early mutation-validation gaps through diagnostic subclass overrides. Test
   operation labels, alias nesting, unchanged validation order, failure cause identity,
   accepted occurrence counts, and recovery after failure.
5. Validate task-consumer reporting and all newly supported adapters through isolation
   and configuration-cache reuse. Run the existing engine and circular-evaluation suites
   against enabled adapters as well as the ordinary implementations.

For every slice: explanation must not evaluate suppliers, run transforms, query producers,
request a fresh mutation attribution, or render configured values. Successful execution
must preserve laziness, upstream liveness, branch selection, side effects, dependencies,
and absence semantics. Do not add complete dependency graphs or mutation histories to
explain a boundary. New metadata/retention costs require measurement before rollout claims.

## Architecture rule for future work

The separation rule is persistent in root `AGENTS.md`. Ordinary engines own semantics;
opt-in adapters own diagnostic interception; shared helpers own provenance decisions;
the independent provenance package owns descriptors/rendering. `ManagedFactories` stays
provenance-free; `ProvenanceManagedFactory` owns its transport integration. This applies
to `DefaultMapProperty` just as it does to scalar, list, set, and derived-provider engines.

D4 does not add source lines, collaborative property semantics, settings-owned property
coverage, file-property coverage, or task-output build-cache metadata. Those require
separate scope decisions. The audit adds no production code and makes no new performance
or complete-coverage claims.
