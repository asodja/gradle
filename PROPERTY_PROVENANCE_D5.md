# D5: Java and Kotlin source locations

D5 adds source-file/line descriptors to property mutation attribution and derived-provider declarations. The existing
experimental property-provenance flag still controls diagnostic property creation.
Contributor identity, accepted mutation semantics, value selection, and producer
dependencies are unchanged. D3 transport and D4 diagnostics carry/render the locations.

## Capture and separation

`InstrumentingClassTransform` uses `PropertyCallSiteInterceptor` to rewrite eligible
Java/Kotlin calls to typed forwarding methods in `PropertyCallSites` and `ProviderCallSites`. The bytecode
supplies source-file and line constants. Capture performs no stack walking, source-file I/O, or Provider evaluation, and
a location descriptor retains no class/method object.
The transform decoration version is also an artifact-transform input, so changes invalidate
previously transformed plugin artifacts when rebuilding the same branch distribution. Interception respects the instrumentation filter.

A thread-local frame scopes metadata to the receiving diagnostic adapter during the
forwarded call. Nested instrumented calls restore the previous frame, including on
failure; other receivers and threads do not inherit the location. `replace` callbacks
run without the outer location so their uninstrumented mutations do not borrow it.
This is the location of the initiating instrumented call, not a stack trace of every
internal or reentrant action performed by that call.

Attribution is enriched in `AttributedProperty` and `CollectionPropertyProvenance`.
The contributor/application registry is not a location cache: two calls from one plugin
can have different locations while retaining the same contributor identity. Rejected
mutations also report the attempted call site, without recording an accepted mutation.

`SourceLocation` is a descriptor containing a file name, positive line number, and optional local source path.
It belongs to the JDK/JSpecify-only model package. Rendering sanitizes and bounds the
file label just like other diagnostic labels. Checkpoint format version 5 carries the
optional location for every transported occurrence and provider-operation descriptor,
plus bounded upstream binding descriptors. This is an internal format, with
no cross-distribution compatibility promise. Value fingerprints remain independent
of provenance metadata.

No provenance fields, branches, bookkeeping, or observer calls are added to
`AbstractProperty`, `AbstractCollectionProperty`, `DefaultProperty`, `DefaultMapProperty`,
or `ManagedFactories`. Transport remains in the existing provenance adapters.

## Traversing scalar property bindings

A named `Property<T>` bound to a tracked provider now includes the upstream chain in
its own failure report. `AttributedProperty` reads the engine's already-selected supplier
through its existing protected capture seam; it does not select a convention or evaluate
a provider again. The runtime helper follows diagnostic inputs while capturing immutable
read snapshots, before evaluation can mutate their configuration. View assembly and
formatting remain deferred until needed. Explicit explanation/checkpoint requests use
the same traversal. No full upstream history is copied on each property mutation.

`DiagnosticProvider` and transported providers retain their declaration/checkpoint through
the ordinary type-sanitizing supplier wrapper. The wrapper still owns type checking,
values and producer dependencies. Known map/filter/etc. frames follow the named property's
binding frame. Each binding transition has an arrow (`→ provider derived from property
'source'`), and its upstream trace is indented one level. Direct property bindings omit
`provider derived from`. Properties on the same task omit the repeated task name;
other tasks and owner scopes retain their identity. Overridden conventions and coverage
notes stay within their property's indentation. Existing branch-selection and zip-input
notes remain in place.

Mutable bindings observe current upstream configuration. Rebinding/unsetting follows the
engine's new supplier immediately. Successful finalization freezes the input descriptors
and releases runtime attribution as before. Shallow copies keep their captured binding,
with live upstream inputs until finalization. Historical transported checkpoints keep their
input history until the recreated property accepts a new mutation. Managed isolation and
configuration-cache codecs use the version-5 descriptor sidecar; ordinary value state and
fingerprints remain unchanged.

Identity-based cycle detection and a 32-binding limit stop live traversal. Descriptor
composition also caps input depth across repeated transport/rebinding. Rendering and
checkpoint codecs have their own bounds; omissions are explicit coverage notes. Thread-local
traversal state is removed on success and failure. No presence checks, callback execution,
producer inspection, source search, or value evaluation are used to discover relationships.

This extension follows scalar `Property<T>` bindings and captured scalar suppliers into
already-tracked chains (including chains originating from list/set/map properties).
Collection destination bindings/contribution graphs, arbitrary untracked provider wrappers,
and new branch/dependency coverage remain outside this increment. Structural `replace`
updates retain their existing local update explanation rather than duplicating its captured
root as an upstream binding. The report describes configuration relationships, not proven
failure causality.

## Coverage and fallbacks

The typed interceptor covers supported scalar/list/set/map `set`, `value`, `convention`,
`unset`, and `unsetConvention` calls; collection `empty`, `add`, `addAll`, `put`, and
`putAll`; and statically dispatched internal append/insert/replace entry points.
Provider overloads forward the original provider without resolving it.
Exact erased descriptors and receiver hierarchy metadata determine eligibility. Known Gradle
API inheritance also works when project instrumentation has no hierarchy index.
Unsupported descriptors retain ordinary dispatch and contributor-only attribution.

`ProviderCallSites` captures declaration locations for `map`, `filter`, `flatMap`, both
`orElse` overloads, provider `zip`, `ProviderFactory.zip`, and map `getting`/`keySet`.
Each forwards construction exactly once, without evaluating providers or user callbacks.
Immutable `ProviderOperation` descriptors retain the operation and optional attribution;
`DiagnosticProvider` carries them and delegates all value and producer behavior to the
existing implementation. Call-site enrichment does not change accepted property mutations.

Transformations and input mutations share one continuous `operation by source (location)`
trace, newest/outermost first: `filter by …`, `map by …`, `set by …`. There are no
separate transformation or input-configuration headings. Each `orElse` or `flatMap`
frame has an indented `selection unknown` note: these are declaration sites, not runtime
branch decisions. Each zip frame says `left input shown; other input not traced` because
D4 tracks the left input only. Map-entry diagnostics retain the absent-key ambiguity
below their own frame. Unattributed declarations explicitly say `unknown caller`.

Kotlin DSL assignment (`property = value`) calls the existing Kotlin `assign` extension
through a separate `PropertyAssignmentCallSites` adapter. It scopes metadata while
leaving assignment behavior in the existing extension. Scalar, multiple-value, and map
assignments support both values and providers; file-property assignments remain outside
this property's provenance scope.

Kotlin SMAP metadata maps generated lines back to the primary source file. Inlined
library bodies, ambiguous/invalid mappings, missing filenames, and nonpositive lines
fall back to contributor-only attribution. Captured file names come from class metadata. Reporting can resolve script paths
using the existing script-origin URI, as described below. Method references, reflective dispatch, generated
classes without usable source metadata, and Groovy dynamic calls do not gain locations.
Groovy sources are deliberately excluded from this interceptor (D7).

## Concise output and navigable locations

Reports use `Configuration of …`, one operation per line, a single space before the
location, and a short `Overridden` section. Collection operations use API spelling
(`addAll`, `putAll`, etc.). Same-scope frames omit redundant build/project labels;
cross-project and cross-build frames retain their source scope. Included-build targets
retain their build identity in the header. Non-root extension and unnamed-property
labels retain their project context, which is already part of task display names. Routine reports have no blanket coverage
footer; derived-provider boundaries and actual partial-coverage reasons remain visible.

For example (the path is illustrative):

```text
Configuration of task ':checkLocations' property 'scalar':
    set by build script (/work/build.gradle.kts:4)

    Overridden:
    convention by plugin 'Locations' (/work/buildSrc/src/main/java/Locations.java:22)
```

Gradle's existing clickable-problem-location change (`755e6107969`, PR #38845) uses
native `path:line` text, not OSC terminal hyperlinks. `PropertyProvenanceRenderer`
follows that convention. It resolves script locations for a file-backed script origin
whose filename matches the captured metadata and whose file still exists. Applied
scripts use their own recorded URI, including when their basename matches the main
build script. Invalid/non-file URIs, mismatched helper filenames, missing files, and
unmapped plugin sources keep `filename:line`. Finalized properties release their attribution
host; later rejected calls also retain that fallback when their script origin is unknown. No source search or download is performed.
Terminal/editor navigation depends on its recognition of `path:line`; output remains
ordinary text in both plain and rich consoles, with no escape sequences embedded in
exception messages or checkpoints.

For local project plugins, provenance-enabled instrumentation records conventional
`src/main/java` and `src/main/kotlin` paths from the original `build/libs/*.jar` or
`build/classes/{java,kotlin}/main` artifact and the class's package/source metadata.
This happens before Gradle copies the JAR into its global cache. The source-aware project
transform uses absolute path sensitivity; byte-identical plugins in different checkouts
must not share embedded paths. Disabled builds retain the normal classpath-normalized
project transform. External artifacts, custom output/source layouts, and sources absent
when reporting retain the filename fallback. No runtime class-loader/source search is used.

Filesystem existence checks happen only when reporting, in the provider-side adapter.
The descriptor renderer accepts a formatting function and still depends only on the
JDK/JSpecify. Paths survive checkpoints; existence is checked again on cache reuse.
No console settings or terminal escape sequences are retained.

## Validation

The D5 tests cover distinct locations for the same contributor, scalar/list/set/map
checkpoint round trips, rejected-operation locations and cleanup, receiver/thread scope,
nested failure restoration, callback isolation, provider laziness, bounded rendering,
all typed interceptor signatures, receiver/dispatch eligibility, and Kotlin source maps.
Integration tests cover Java plugins and Kotlin DSL calls/assignments, configuration-cache
store/reuse with and without Isolated Projects, and disabled diagnostics.

The preceding D5 implementation checkpoint passed **2,555 targeted tests:** 2,456 model-core unit tests, 53 embedded integration
tests, 38 classpath tests, and 8 core tests, with no failures, errors, or skips.
Java Checkstyle, Groovy CodeNarc, and Kotlin Detekt checks passed. All 17 descriptor
sources compile with only the JDK and JSpecify; 27 compiled ordinary engine/nested
classes have no provenance references. No full repository suite or performance
campaign is claimed.

The provider-declaration extension covers all supported operations, lazy construction,
Gradle API inheritance without a hierarchy index, source-path constants, and buildSrc /
included-build plugin paths across configuration-cache reuse with and without isolation.
The local demo at `/home/agent/test-projects/property-provenance-demo` was refreshed and
checked: `explain` succeeds with local plugin paths; `derivedMissing` intentionally fails
with distinct map/filter locations, including after configuration-cache reuse.

The continuous-trace report revision passed 112 targeted tests (100 model unit tests
and 12 embedded integration tests), plus model Java Checkstyle and Groovy CodeNarc.
The demo now applies the registered `demo.provenance` plugin ID and declares its failure
chains during configuration. Its missing-value failure retains identical operation,
plugin-ID, and source-location frames after cache reuse; all nine successful provider
examples were also checked with the revised output.

The scalar binding traversal extension passed **2,517 targeted tests**: 2,462 model
unit tests and 55 embedded integration tests, with no failures, errors, or skips. Model
Java Checkstyle and Groovy CodeNarc passed. All 17 descriptor sources still compile with
only the JDK/JSpecify; 27 ordinary engine/nested class files contain no provenance
references. Tests cover live source changes, selected conventions, finalization, shallow
copies, managed isolation, historical restoration, cycles, deep/repeated transport,
pre-evaluation snapshot consistency, and configuration-cache reuse with/without isolation.
The demo's named `missingValue` task now only queries its property; the final error itself
prints its binding and six upstream transformations, without an explicit trace-printing
workaround. A final focused run passed another 100 tests (98 model unit and two
embedded integration cases), plus style checks, after consolidating overridden sections.
The demo failure was rechecked with the final renderer and matched after cache reuse.

The arrow-boundary renderer passed 104 focused tests (102 model unit tests and two
embedded integration cases), Java Checkstyle and Groovy CodeNarc. The demo's final error
shows the nested upstream chain and property-scoped overridden conventions, and the
rendered error matches after configuration-cache reuse.

## Next work

D6 is the allocation/timing/retained-heap and rollout campaign. This increment does not
claim an overhead budget: interception still forwards through helper calls when ordinary
properties are used, and diagnostic mutation calls allocate scoped metadata. Measure
both disabled and enabled modes, including Kotlin assignment forwarding, before rollout.
Runtime branch selection, flatMap-selected provider context, arbitrary factory providers,
and zip right-input history remain separate evaluation/dependency coverage work.
D7 adds Groovy locations last. Existing property-kind and collaboration scope exclusions
from D4 still apply.
