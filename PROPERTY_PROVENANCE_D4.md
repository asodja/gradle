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

# Property provenance: D4 broader diagnostics

Implemented on D3 commit `2cab346d789`. The preceding
[gap audit](PROPERTY_PROVENANCE_D4_RESEARCH.md) records the source investigation;
this checkpoint describes the resulting implementation.

## Provider boundaries

Opt-in diagnostic scalar/list/set/map properties, diagnostic snapshots, and transported
providers retain diagnostic context when deriving `map`, `filter`, `flatMap`, either
`orElse` overload, or `zip`. Map `getting` and `keySet` are included.
`DiagnosticProvider` delegates value, presence, execution-time, and producer calculation
to the existing provider implementation. No alternate evaluator is introduced.

`EffectiveProvenanceView.ProviderBoundary` records operation kinds separately from
accepted mutation occurrences. Deriving a provider requests no mutation attribution and
creates no accepted occurrence. A direct derived provider follows its live input's
configuration; derivation from a snapshot retains the snapshot's captured input context.
The bounded renderer labels that context as **known input configuration**, with partial
coverage. It does not claim that it is the selected source of a branching provider.

The adapter deliberately does not inspect/evaluate the other input of `orElse`/`zip` or
the provider returned by `flatMap` merely to explain a failure. Actual branch selection
and failure causality remain uninferred. Operations initiated on an ordinary untracked
provider stay untracked, including cases where an eventual other input is tracked.
This is opt-in boundary reporting, not global provider-graph tracking.

For a missing map entry, the report explains that either the map or the key can be
absent; it does not evaluate the map again to distinguish them. Descriptors contain no
keys, values, per-entry ownership, transformer objects, or runtime attribution hosts.
The structural replacement classifier recognizes the diagnostic wrapper around built-in
maps, retaining its existing 128-map inspection limit and partial fallback.

## Evaluation and mutation failures

Diagnostic query boundaries capture immutable `ProvenanceReadSnapshot` references
**before** evaluation, since user code can mutate/rebind the source during a failed read.
The snapshot assembles an `EffectiveProvenanceView` only when a failure needs reporting.
It retains the recorded name, source, update sequence, convention, and derived boundaries;
it never defers a lookup into mutable property state. Finalized/restored snapshots reuse
the complete existing immutable view. Required/nullable/default queries,
presence, internal value calculation, execution-time calculation, producer lookup, and
explicit finalization have failure context. Public query guards also cover deferred side
effects that execute after a `Value` has been calculated. Successful nullable/presence
reads of absent values retain their normal behavior.

Missing required values retain the existing `MissingValueException` report with the
original exception as direct cause. Existing mutation IllegalArgumentException and
IllegalStateException decoration remains, and early null-validation failures retain the
NullPointerException category and original cause. Diagnostic subclass forwarding now
covers early collection `add`/provider `addAll`, map `put`/provider `putAll`, and invalid
dynamic assignment. Existing aliases and append/insert context retain their operation
labels. Failed operations create no accepted occurrences; engine validation order remains
unchanged. Arbitrary `replace` callback failures include the attempted operation.

For arbitrary evaluation exceptions, the original exception object, subtype, message,
and cause chain remain intact. The adapter attaches a stackless suppressed diagnostic
context, rendered only on failure. Already-reported contexts in the cause chain are not
added repeatedly. This context is visible in stacktrace output; it does not replace the
ordinary console summary with a different exception category. Exceptions that disable
suppression cannot receive this added context. JVM `Error` instances are not intercepted.
Unavailable diagnostic metadata falls back to the original operation/failure.

## Task validation and transport

Required task input validation uses presence rather than a required `get()`. The task
validation consumer captures a lightweight diagnostic snapshot for required properties
before that presence check, and skips this validation snapshot for optional properties.
Only the missing-required-value branch materializes the view into a local `checkpoint`
variable and adds it to the existing problem details. Problem ID, contextual label,
documentation link, and suggested fixes remain unchanged. Untracked validation keeps its
original details. This integration is outside the provider engines.

Checkpoint format version 2 transports provider-boundary descriptors along with the D3
local configuration. Unsupported versions fail explicitly; this internal transport format
is not a promise of compatibility between Gradle distributions. The existing provider
codecs carry the new checkpoint; managed factories still see only ordinary value state
through `ProvenanceManagedFactory`. Value fingerprints do not include the descriptors.

## Separation and validation

No D4 changes are made to `AbstractProperty`, `AbstractCollectionProperty`,
`DefaultMapProperty`, `DefaultProperty`, or `ManagedFactories`. Compiled-class checks
(including their nested classes) find no provenance dependencies in these engines.
All 15 descriptor-package sources compile with only the JDK and JSpecify.
Diagnostic subclasses contain typed forwarding; shared helpers contain diagnostic policy.
The root `AGENTS.md` records this boundary for future work.

Validation includes ordinary/diagnostic property and circular-evaluation regressions;
derived missing and transform-produced missing values; live versus captured context;
unused fallback laziness; preserved side effects; failures across all property kinds;
unsafe reads; failed finalization and recovery; early validation; isolation round trips;
and configuration-cache store/reuse with and without Isolated Projects. Changing derived
values retain producer dependencies on cache reuse. Task validation checks preserve the
Problems API ID and solutions in both enabled and disabled modes.

A stale D3 unit assertion was corrected: enabled legacy scalar factory methods now create
diagnostic properties, as required by the D3 managed-factory decorator. Disabled legacy
factory methods still create ordinary properties.

**Initial D4 validation: 2,457 targeted tests passed:** 2,412 model-core unit tests, 3 core validation unit tests,
and 42 embedded integration tests, with no failures, errors, or skips. This includes
65 D4 unit cases and 8 D4 integration cases. Model-core/core Java Checkstyle and
model-core unit/integration Groovy CodeNarc passed. A 4,096-operation derived chain
can be explained and checkpointed without recursion or supplier evaluation.

The read-snapshot refinement passed 538 targeted tests: 517 model-core unit tests,
7 core validation tests, and 14 embedded integration tests. These verify capture before
presence evaluation, no validation capture for optional properties, view materialization
only for missing required values, immutable state/name capture, preservation of existing
finalized/restored views, and configuration-cache reuse. Java Checkstyle and Groovy
CodeNarc checks passed; the 15-file model still compiles with only JDK/JSpecify dependencies.

No full repository test suite or new performance/retention campaign is claimed. D4 introduces adapter objects
and pre-evaluation descriptor-reference snapshots, including on successful diagnostic reads.
Full view assembly is deferred until needed by reporting. D6 must
measure these costs; the earlier D1/D2 performance numbers do not establish D4 overhead.

## Next work

D5 adds Java/Kotlin source locations. D6 covers broader validation, retained heap,
allocation/timing budgets, and rollout. D7 adds Groovy locations last. Settings-owned
properties, file properties, configurable file collections, collaboration semantics, and
task-output build-cache metadata remain outside this increment.
