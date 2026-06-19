# Plan v2: in-place eager-shim getters via a marker annotation

Issue: https://github.com/gradle/gradle/issues/24251
Supersedes the chosen design in [PLAN.md](PLAN.md) (strip-to-abstract). Read PLAN.md first for the
problem statement and the rejected pure in-place attempt.

## Why a v2

The strip-to-abstract design in PLAN.md works only when the **upgraded getter in the parent is
abstract** (the managed `@ReplacesEagerProperty public abstract Property<T> getX()` shape). For a
**concrete** upgraded getter it breaks: `AbstractClassGenerator.claimPropertyImplementation` refuses to
claim a property that has any non-bridge concrete getter (`shouldImplement() && !isAbstract()`), so the
parent's concrete upgraded getter prevents claiming, and the stripped abstract eager getter is left
unimplemented → `AbstractMethodError` / `Cannot have abstract method`.

This is not theoretical: of 366 `@ReplacesEagerProperty` declarations only ~217 are immediately
`public abstract`; the rest include concrete upgraded getters (e.g. `CreateStartScripts.getOptsEnvironmentVar()`,
`CreateStartScripts.getUnixScript()`, `Checkstyle.getIsIgnoreFailures()`, `CompileOptions.getAllCompilerArgs()`).
A plugin overriding one of the real (non-derived) ones would not be handled by v1.

v1 (strip-to-abstract) and the rejected pure in-place attempt are exact mirror images:

| Parent upgraded getter | strip-to-abstract (v1) | pure in-place (rejected) |
|---|---|---|
| **abstract** (managed) | works | fails — concrete legacy getter orphans the abstract `Property` getter |
| **concrete** | fails — concrete `Property` getter blocks claiming, orphans the stripped abstract getter | works — nothing to orphan; rewritten body calls the concrete parent getter |

v2 makes the **in-place** approach work for the abstract-parent case too, unifying both.

## The idea

Keep the override **concrete** with a rewritten body, and tag it with a marker annotation. The class
generator treats an annotated getter as *not present* for property-claiming and does not regenerate it.

- **Marker annotation** — e.g. `@UpgradedPropertyEagerShim`, `@Retention(RUNTIME)`, declared in
  `internal-instrumentation-api` so both the classpath transform and the model-core generator can see it.
  RUNTIME retention is load-bearing: the generator reads it via `MethodMetadata.isAnnotationPresent`.

### Instrumentation — `InstrumentingClassTransform`

For a pure super-delegating override of a replaced accessor (same detection as v1: JVM `INVOKESPECIAL`,
statically-compiled Groovy, Kotlin trailing `Intrinsics` null-check, dynamic Groovy
`ScriptBytecodeAdapter.invokeMethodOnSuper0`):

1. **Rewrite the body** instead of stripping it: reinterpret the `super.getX()` `INVOKESPECIAL` as a
   virtual read by offering it to `BYTECODE_UPGRADE` interceptors with `effectiveOpcode = INVOKEVIRTUAL`
   (the `effectiveOpcode` change from the original in-place attempt). The existing read-interception
   then rewrites it to call the upgraded `this.getX()` and adapt the result back to the legacy return
   type. This works whether the parent's upgraded getter is **abstract** (virtual dispatch → the managed
   impl) or **concrete** (virtual dispatch → the parent's impl).
2. **Add `@UpgradedPropertyEagerShim`** to the method.

No strip-to-abstract; the getter stays concrete with a correct body. Works on both abstract and
non-abstract plugin classes with no bytecode-shape concerns.

### Class generator — `AbstractClassGenerator` (all gated on the annotation)

- `claimPropertyImplementation`: skip annotated getters in the "concrete getter blocks claiming" check —
  `if (getter.shouldImplement() && !getter.isAbstract() && !isEagerShim(getter)) return false;`
- `allGettersAreAbstract`: treat an annotated getter as absent.
- `addGetter` / `mainGetter`: exclude annotated getters from `mainGetter` selection so the property type
  resolves to the upgraded `Property` getter (replaces v1's "prefer Property type" heuristic with an
  explicit rule).
- read-only generation loop: **skip** annotated getters — do not regenerate them; `*_Decorated` inherits
  the instrumentation-rewritten concrete getter as-is.

`assertNotAbstract` needs no change: the annotated getter is concrete, and the upgraded `Property` getter
is now claimed (managed) so it is never orphaned.

## How it covers both cases

- **Abstract parent** (e.g. `Checkstyle.getMaxErrors()`): the annotated concrete `int` getter is ignored
  → the abstract `Property<Integer>` getter is claimed/managed and implemented in `*_Decorated`; the
  concrete shim's rewritten body reads it. ✓
- **Concrete parent** (e.g. `CreateStartScripts.getOptsEnvironmentVar()`): the annotated getter is ignored
  → only the parent's concrete `Property` getter remains (correctly *not* managed, no abstract getter to
  orphan); the concrete shim's rewritten body calls the parent's concrete getter. ✓

## What v2 removes vs v1

- No strip-to-abstract → no abstract-method-in-a-concrete-class, no `isAbstractClass` guard question, no
  undecorated-instantiation `AbstractMethodError` caveat (the getter is always concrete with a body).
- No `AsmBackedClassGenerator.applyEagerShimGetter` and no `ManagedObjectFactory.unpackEagerShim` — the
  body comes from the read-interception's existing adaptation (`getOrElse`/cast). The generator change is
  purely "ignore marked getters".
- No `isEagerShimGetter` return-type heuristic — replaced by the explicit annotation.

Net generator footprint: ~4 small annotation-gated checks; net instrumentation footprint: the
`effectiveOpcode` reinterpretation + adding the annotation.

## Tests

- Reuse the v1 cross-version scenarios (`CustomCheckstyle` overriding `int getMaxErrors` and
  `FileCollection getCheckstyleClasspath`, across Java/Groovy[static+dynamic]/Kotlin) — these exercise
  the **abstract-parent** path.
- Add a **concrete-parent** override scenario that v1 could not handle — a plugin overriding a concrete
  upgraded getter (e.g. `CreateStartScripts.getOptsEnvironmentVar()` / `getUnixScript()`), confirming the
  value round-trips and decoration succeeds. This is the case that distinguishes v2 from v1.
- Keep the metadata-store re-annotation unit test (annotations are preserved naturally — the method and
  its annotations are untouched, only the body is rewritten).
- Generator unit coverage for "annotated getter is ignored for claiming and not regenerated", if it can
  be expressed without the cross-version harness.

## Validation

- In-sandbox: modules compile, unit tests pass (no daemon forking).
- Cross-version integration tests cannot run green in the Claude sandbox (forked daemons hit `ps`,
  FSEvents, and a Kotlin-compiler temp-lock); authoritative run is
  `./gradlew :integ-test:gradle8.0.2CrossVersionTest --tests "*PropertyUpgradesBinaryCompatibilityCrossVersionSpec"`
  in a normal environment.

## Risks / open questions

- **Novel and unvalidated.** v1 (strip-to-abstract) is validated (11/12 cross-version, the 1 failure
  unrelated). v2 is cleaner and more complete but unproven end-to-end; prototype on a side branch so v1
  stays intact until v2's cross-version run is green.
- **Annotation retention/visibility.** Must be RUNTIME and on a module both layers depend on; the
  generator must actually observe it on the instrumentation-rewritten method.
- **Non-pure overrides** (e.g. `return super.getX() * 2`) are still out of scope — only pure
  super-delegations are rewritten + annotated.
- **`mainGetter` exclusion blast radius.** Excluding annotated getters from `mainGetter` is narrow
  (only instrumentation-marked methods), but worth a wider decorated-types check.
- **Scalar vs container.** As in v1, scalar `Property<E>` is the focus; `ListProperty`/`SetProperty`/
  `MapProperty` adaptation in the read-interception should be confirmed for the rewritten-body path.
