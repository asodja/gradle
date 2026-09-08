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

# Property provenance: D2 collection checkpoint

For the subsequent consolidation within the diagnostic collection adapters, see
[the collection diagnostic adapters checkpoint](PROPERTY_PROVENANCE_COLLECTION_HOOKS.md).

## Roadmap and semantics reconciliation

This checkpoint implements **D2 collection provenance**, following roadmap commit
`d71ed7bafde06b54faa1810b64f563c41616ddb0`. It starts from D1 commit
`2eb3ddf56f711aa773fe52ca29044375e4d75f42` and preserves the unmodified S0 Gradle
baseline `d41e66c4e92e3a79dfeeb8e5f2c6912f61d5d036`.

The previous checkpoint documents refer to the older numbering. The current order is:
D1 scalar reports; **D2 collection provenance**; D3 scalar/collection transport;
D4 broader diagnostics; D5 Java/Kotlin locations; D6 rollout; D7 Groovy locations last.
C1 reuses D1; C3 reuses D3, including D2's collection prerequisite.

The shared contract is unchanged by the roadmap revision. The semantics repository
remains at pinned revision `0a695690ac2a4852b7d455a31b1adf830345076c` in both the local checkout and the upstream `main` ref checked during D2. Its implementation discussion calls for retaining individual collection
operations instead of reducing diagnostics to the last contribution. This implementation
records distinct effective contributions, not element origins or complete mutation history.
The upstream discussion of self-reference classification and runtime collaboration does
not override the clean-baseline contract: direct self-assignment stays cyclic, and only
recognized replace/map shapes preserve a captured update root. No authorization or
ordering policy is added.

## Scope and activation

The existing internal switch now enables project-owned `ListProperty`, `SetProperty`
and `MapProperty`, in addition to scalars:

```sh
./gradlew --no-configuration-cache -Dorg.gradle.internal.property-provenance=true help
```

This is a consumer-build example. The Gradle repository's own validation retains its
normal configuration-cache and Isolated Projects settings. Provenance transport through
consumer isolation and configuration-cache store/hit is **D3 and is not implemented**.
Settings-origin configuration of project properties remains supported. Settings-owned
properties, file properties and `ConfigurableFileCollection` gain no new coverage.

Managed properties retain their declared name; standalone properties use
`'unnamed property'`. An explicit internal `configurationTrace` getter explains the
current configuration without querying values or producers. Required `get()` and
snapshot `get()` report missing values. Selected mutation rejections preserve the
original exception category and original exception as their direct cause.

## Representation and ordinary behavior

`SemanticOperation.Kind.CONTRIBUTION` distinguishes collection contributions from
scalar structural updates. Shapes distinguish add/addAll, append/appendAll,
put/putAll and insert/insertAll. A bulk call is one occurrence, regardless of element
or key count. Contributions and recognized map updates share the persistent
`UpdateSequence`, with constant-time append and immutable shared prefixes.

The default empty and missing collection sources are descriptor-only facts, distinct
from an explicit binding to a missing provider. They carry no fabricated author.
The collection adapters supply source-selection facts from the existing engine:

- `add`/`put` and their bulk variants select the explicit source or the engine default,
  discarding an implicit convention. That convention appears as shadowed configuration.
- `append`/`insert` and their bulk variants preserve a current convention as the explicit
  source before contributing. The convention's original occurrence survives; the implicit
  promotion does not invent a second accepted occurrence for the contribution call.
- A binding to an explicit missing provider remains selected. Its provider-backed plan
  still retains subsequent contributions; a convention does not become a fallback.
- `set(null)` changes the collection default to missing according to the existing engine.
  An addition absorbed by the engine's concrete no-value supplier receives an accepted
  occurrence but is not added to the effective contribution sequence. No provider presence
  query is used to make this distinction.
- Set deduplication and map key overwrites do not imply per-element blame. Both contributing
  operations remain part of the effective supplier plan; no keys or values enter descriptors.
- Source rebinding drops displaced effective contributions. `unset` selects the current
  convention/default. Changing a shadowed convention does not rewrite captured contributions.
- Recognized `replace { previous -> previous.map(...) }` retains the captured source and
  contribution prefix, then appends one structural update. An unrecognized replacement is
  an explicitly partial binding; it is not evidence of independence or authority.

The implementation reuses `OrdinaryProvenanceState`, descriptors and `ProvenanceRenderer`
in the provider-independent package. `CollectionPropertyProvenance` holds the runtime
attribution service outside that package. Three enabled property subclasses adapt the
existing engines. The default list/set/map property layouts have no added fields.
The collection/map engines expose narrow protected seams for contribution attachment,
convention preservation and recognition of their concrete no-value supplier. Their
supplier construction and evaluation algorithms are unchanged.

Successful mutations perform no formatting or stack capture. The renderer emits reverse
contribution/update frames followed by the selected source, with shadowed conventions
separate. Its existing output limits remain reporting limits, not metadata truncation.
Provider dependencies never become accepted local contributions. Ordinary presence,
nullable reads and producer/dependency behavior remain governed by the existing engine.

## Copies, finalization and failures

Snapshots capture supplier and descriptor references directly. They do not retain the
mutable descriptor state or attribution host, and construct reporting views only when
requested. Rebinding the property does not change a copy; upstream suppliers remain live
until finalization. Finalization freezes the pre-evaluation provenance checkpoint only
after the engine succeeds, then releases the added attribution service. Finalize-on-read
uses the same path. Describing or copying a property does not capture a new origin.

Rejected set/convention/clear/contribution/replacement operations do not add accepted
occurrences. The original engine exception remains the direct cause. After finalization,
the rejected caller is honestly unknown because the attribution service was released.
The existing validation exceptions that occur before contribution attachment (for example,
null-entry checks) retain their original behavior; D2 does not add universal exception
interception. Arbitrary transform failures and derived provider-boundary reports remain D4.

An existing engine limitation remains: empty and fixed collection suppliers are inner
objects and can retain their originating property through a snapshot. Populated collecting
suppliers are static and the new snapshot adds no owner backreference. The performance
probe compares empty, populated and finalized snapshot retention against the unmodified
baseline, and separately checks unwanted metadata/runtime references. No general claim
that every engine snapshot releases its owner is made.

## Validation

**1,727 tests passed:** 1,699 unit tests and 28 embedded integration tests. This includes
62 focused collection cases and 272 inherited circular-evaluation cases running against
enabled collection subclasses. Java Checkstyle and Groovy CodeNarc passed. The complete
Gradle repository test suite and production rollout checks were not run.

Focused validation covers all three collection types, default/convention selection,
explicit missing providers, source rebinding, individual/bulk/varargs contributions,
set duplicates, map overwrites, named copies, live upstream changes, finalization and
finalize-on-read, rejected mutations, null replacement, and lazy explanation. Existing
circular-evaluation suites also run with enabled collection subclasses. The ordinary
baseline suites continue checking their original values, messages and dependency behavior.
The shared test codec round-trips collection defaults and every contribution shape;
it is not a production codec or evidence of D3 completion.

Integration fixtures cover managed extension names, repeated plugin contributions,
missing reports, copies/finalization, settings-origin project configuration and disabled
failures. Existing scalar attribution and diagnostics fixtures run alongside them.

Reproduce the focused checks:

```sh
./gradlew :model-core:test \
  --tests '*DiagnosticCollectionPropertyTest*' --tests '*DiagnosticPropertyTest' \
  --tests '*AttributedPropertyTest' --tests '*EffectivePropertyProvenanceTest' \
  --tests '*SharedProvenanceContractTest' --tests '*OrdinaryProvenanceStateTest' \
  --tests '*ProvenanceRendererTest' --tests '*DefaultListPropertyTest*' \
  --tests '*DefaultSetPropertyTest*' --tests '*MapPropertySpec*' \
  :model-core:embeddedIntegTest \
  --tests '*CollectionPropertyProvenanceIntegrationTest' \
  --tests '*PropertyAttributionIntegrationTest' \
  --tests '*PropertyProvenanceDiagnosticsIntegrationTest' \
  :model-core:checkstyleMain :model-core:codenarcTest :model-core:codenarcIntegTest \
  --max-workers=4
```

## Focused performance evidence

The reproducible runner uses the retained unmodified S0 distribution, current compiled
classes with provenance disabled, and enabled collection factories. It uses three rotated
JVM forks, five warmup/five measurement batches, a 256 MiB fixed heap and Serial GC.
Attribution descriptors are shared and prebuilt. It measures construction, bindings,
contributions, copies and finalization, and constructs chains of 0, 1, 8, 128 and 4,096
contributions. Standalone compilation of the descriptor package uses only JDK/JSpecify.

```sh
python3 testing/performance/provenance/run-collection-probe.py \
  --baseline-zip /tmp/provenance-s0-20260907/gradle-9.9.0-bin.zip \
  --output testing/performance/provenance/d2-collections-probe-20260907.json
```

[Raw allocation and retention evidence](testing/performance/provenance/d2-collections-probe-20260907.json)
and [the final source/test manifest](testing/performance/provenance/d2-evidence-20260907.json)
record the measured implementation. Medians below combine the five samples from each
of three JVM forks; values are allocated bytes per operation, not retained heap.

| Measurement | Baseline | Compiled in, disabled | Enabled |
| --- | ---: | ---: | ---: |
| List/set shallow property size | 40 | 40 | 48 |
| Map shallow property size | 48 | 48 | 56 |
| List/set binding | 176 | 176 | 240 |
| Map binding | 200 | 200 | 264 |
| Copy, all three types | 24 | 24 | 40 |
| List/set contribution plus source reset | 248 | 248 | 368 |
| Map contribution plus source reset | 272 | 248 | 368 |
| List, construct 4,096 contributions | 345,376 | 345,376 | 574,992 |
| Set, construct 4,096 contributions | 345,376 | 345,392 | 574,992 |
| Map, construct 4,096 contributions | 345,432 | 345,448 | 575,064 |

Enabled properties additionally hold a 32-byte runtime adapter and a 48-byte descriptor
state; the shallow property sizes do not include these objects, namespace strings or
engine suppliers. Each retained contribution adds a 32-byte occurrence and a 24-byte
persistent sequence node. The observed chain deltas are consistent with **56 bytes per
contribution plus fixed setup**, rather than copying the prior sequence on every append.
Bindings allocate 64 bytes of source/occurrence metadata in this probe. The bounded renderer
never requires truncating the retained effective sequence.

Small disabled differences in set/map workloads vary with JIT allocation elimination;
they do not establish a performance regression or improvement. The primitive timing
samples are not a controlled throughput benchmark. No production runtime percentage
claim follows from this probe.

All 36 enabled unwanted-reference checks (three types, four scenarios, three forks)
reported zero. In separate matched engine-snapshot checks, empty and finalized snapshots
retained their owner in all variants, while populated snapshots retained their owner in
the baseline/disabled variants but released it when enabled. This confirms the inherited
supplier limitation described above rather than claiming universal owner release.

A preliminary run identified eager view allocation during copying. The final implementation
captures descriptor references directly, reducing measured enabled copy allocation from
184 to 40 bytes. Only the final run is included as D2 evidence.
Earlier S0–D1 raw evidence remains unchanged. This is bounded allocation/scaling and
retention evidence, not a production throughput, heap or configuration-cache guarantee.
No long performance campaign, source-line instrumentation or production rollout is included.

## Review boundary

D2 stops before D3. The next milestone must preserve scalar and collection descriptors,
occurrence identities, effective contributions and snapshots through managed recreation,
isolation and cache store/hit without treating deserialization as a mutation. Runtime
collaborative collection semantics remain C2. No new blocking semantic decision was needed;
transport identity/compatibility and broader failure coverage remain their planned milestones.
