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

# Property provenance: isolation and configuration-cache transport

This checkpoint follows the squashed D2 implementation `af729ae96c9`. D3 preserves
ordinary scalar and collection provenance through managed isolation, isolation
serialization, and configuration-cache store/load. The roadmap and shared semantic
contract remain those pinned in [D2](PROPERTY_PROVENANCE_D2.md).

## Transport boundary

`ProvenanceCheckpoint` lives in the provider-independent provenance package. Its
versioned binary representation carries the target owner and model name, source
selection and knowledge, contributor/origin descriptors, application tokens, effective
updates and collection contributions, shadowed configuration, partial-coverage reasons,
and the last accepted occurrence. Existing occurrence scope/sequence pairs are retained.
Unknown format versions are rejected. Long labels and update sequences are independent
of renderer limits. Encoding and decoding contain no Provider queries, runtime
application objects, project hosts, plugin instances, values, or class loaders.

`ProvenanceAware` exposes read-only checkpoints for diagnostic properties and snapshots.
`RestorableProvenance` brackets engine state hydration. The adapters suppress accepted
binding/clear bookkeeping while installing transported state, then restore the recorded
checkpoint. Hydration does not request attribution or create a mutation occurrence.
The receiving property factory supplies its normal read policy. Subsequent mutations
use receiving attribution where available, otherwise honest unknown attribution.

Recreated mutable properties get fresh namespaces for future mutations. This preserves
old identities while preventing two independently recreated branches from allocating
the same ID to different new operations. No persistent contributor identity is inferred
from the new namespace.

## Isolation and value fingerprints

`DefaultIsolatableFactory` captures provenance before unpacking or isolating the provider
state: either operation can evaluate user code that mutates the original property.
`IsolatedManagedValue` carries optional checkpoint bytes beside its ordinary isolated
value. Both the isolation serializer and the configuration-cache isolated-value codec
preserve that sidecar. A `ProvenanceManagedFactory` decorator handles the value-plus-checkpoint
envelope at service registration. It delegates value reconstruction to the ordinary managed
factories with a property factory that brackets hydration, then restores the checkpoint.
The ordinary `ManagedFactories` remain unaware of provenance and use their existing
value representations. Provider snapshots are decorated after ordinary reconstruction.

Provenance is excluded from value snapshots and their fingerprints. The ordinary
`unpackState()` representations remain unchanged. Existing scalar type inference and
collection element/key/value type erasure during managed isolation are also unchanged.
Configuration-cache property codecs continue transporting declared types explicitly.

Recreation follows the ordinary engine's value/plan and lifecycle behavior. In particular,
it does not restore a live convention that the ordinary recreation path has discarded.
The historical checkpoint remains available for explanation until the next accepted
mutation, after which bookkeeping follows the recreated engine state. An isolated missing
collection retains the ordinary missing default. Unnamed properties retain their ordinary
failure wording.

## Configuration cache

The scalar, list, set and map property codecs write checkpoints inside their existing
object-identity boundaries and restore properties before applying the existing mutability
restriction. The Provider codec separately preserves directly transported diagnostic
snapshots, including providers with changing execution-time values. Supplier evaluation,
producer dependencies, and aliases remain handled by the ordinary codecs and engines.

The provenance enablement flag is part of the configuration-cache key. Switching between
enabled and disabled provenance cannot reuse an entry from the other mode. Disabled
properties remain ordinary property implementations; no provenance model is allocated
for them. The codecs write an absent-checkpoint marker for untracked objects.

Example for a consumer build using the prototype distribution:

```sh
./gradlew -Dorg.gradle.internal.property-provenance=true --configuration-cache report
```

This concerns the configuration cache, not task-output build-cache storage.

## Validation

**2,373 tests passed:** 2,339 unit cases and 34 embedded integration cases, including
64 focused transport unit cases and six transport integration cases. Java Checkstyle,
Groovy CodeNarc and Kotlin Detekt passed. The full repository suite was not run.

Focused tests cover versioned descriptor round trips, every semantic operation, long
labels and 4,096 contributions; direct and serialized isolation of all four property
types and their snapshots; missing values; unchanged fingerprints; independent mutation
identities; lazy checkpoint capture; and supplier mutations during isolation.

Integration tests compare checkpoint bytes and failure reports on configuration-cache
store and reuse, with and without Isolated Projects. They also check enabled/disabled
entry separation and a changing task-produced value: after its input file changes, the
producing task runs and both the property and its snapshot see the new value on a cache
hit. A property alias retains object identity.

The ordinary property, collection, provenance, value-snapshot, and isolation regression
suites accompany these tests. Java Checkstyle, Groovy CodeNarc, and Kotlin Detekt cover
the affected sources. The descriptor package also compiles with only the JDK and JSpecify.

Representative focused commands:

```sh
./gradlew :model-core:test --tests '*PropertyProvenanceTransportTest' \
  --tests '*DefaultIsolatableFactoryTest' --tests '*IsolatableSerializerRegistryTest' \
  --tests '*DefaultValueSnapshotterTest' \
  :model-core:embeddedIntegTest --tests '*PropertyProvenanceTransportIntegrationTest' \
  :model-core:checkstyleMain :model-core:codenarcTest :model-core:codenarcIntegTest \
  :core-serialization-codecs:detekt :configuration-cache:detekt --max-workers=4
```

This increment adds optional storage to isolated managed values and checkpoint data to
serialization. It does not establish a new allocation, retained-heap, cache-size, or
whole-build throughput budget; the September 7 performance artifacts describe D2 only.

## Remaining scope

D4 remains broader diagnostic coverage, followed by D5 Java/Kotlin source locations,
D6 validation/rollout, and D7 Groovy locations. File properties, configurable file
collections, settings-owned property attribution, and collaborative correctness traces
are not added by D3. This is ordinary effective-provenance transport, not a new mutation
policy, complete mutation history, or provider-dependency blame model.
