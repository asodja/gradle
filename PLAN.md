# Plan: binary-compatible plugin overrides of upgraded property getters

Issue: https://github.com/gradle/gradle/issues/24251

## Problem

When Gradle "upgrades" an eager property to the Provider API, the accessor changes shape, e.g.
`Checkstyle`:

```java
// before (Gradle <= 8.x)
public int getMaxErrors();

// after (current), annotated @ReplacesEagerProperty(originalType = int.class)
public abstract Property<Integer> getMaxErrors();
```

Reads of the old accessor from already-compiled plugins are already handled by the existing
**call-site interception** (`PropertyUpgradeClassSourceGenerator` / the `BYTECODE_UPGRADE`
interceptors): an `INVOKEVIRTUAL getMaxErrors()I` is rewritten to call the upgraded getter and
adapt back to `int`.

The gap is **overrides**. A plugin compiled against the old API may subclass and override the eager
accessor with a pure super-delegation:

```groovy
abstract class CustomCheckstyle extends Checkstyle {
    @Override int getMaxErrors() { return super.getMaxErrors() }                 // primitive upgrade
    @Override FileCollection getCheckstyleClasspath() { return super.getCheckstyleClasspath() } // subtype upgrade
}
```

On the new Gradle:
- The override's `super.getMaxErrors()` is `INVOKESPECIAL Checkstyle.getMaxErrors()I`, which no longer
  resolves (the super accessor is now `()Lorg/gradle/api/provider/Property;` and abstract).
- The decorated type ends up with **two same-named getters of different return types** — the legacy
  eager one and the upgraded `Property` one — which the class generator cannot decorate as-is.

Goal: such overrides keep working (binary compatibility), with the property still behaving as the
managed, lazy, upgraded property.

## Rejected approach: rewrite the override body in place

**Idea.** In `InstrumentingClassTransform`, reinterpret the override's `super` call: offer an
`INVOKESPECIAL` to a replaced accessor to the property-upgrade interceptors as if it were an
`INVOKEVIRTUAL` read on `this`. The existing read-interception then rewrites it to call the upgraded
getter and adapt to the legacy type. The override stays **concrete** with a corrected body; no class
generator changes, no eager-shim machinery. (A one-line change in the method visitor.)

**Why it does not work.** The class generator only gives a property a managed backing field when
**every getter of that property is abstract**:

```java
// AbstractClassGenerator.claimPropertyImplementation
for (MethodMetadata getter : property.getters) {
    if (getter.shouldImplement() && !getter.isAbstract()) {
        return false; // a concrete getter => the property is NOT claimed as managed
    }
}
```

This invariant is load-bearing: an abstract getter has no body to read a field from, so "all getters
abstract" is how the generator knows the user is not hand-managing the backing field
(see the comment on `allGettersAreAbstract`). Keeping the eager getter **concrete** therefore prevents
`maxErrors` from being claimed as managed, the inherited abstract `Property<Integer> getMaxErrors()` is
never implemented, and decoration fails:

```
java.lang.IllegalArgumentException: Cannot have abstract method Checkstyle.getMaxErrors(): Property<Integer>.
    at AbstractClassGenerator.assertNotAbstract(...)
```

Even if we relaxed claiming, the managed read-only loop regenerates **every** getter as a raw field
read (`GETFIELD __maxErrors__ : I`), which fails at runtime because the backing field is `Provider`-typed
(`NoSuchFieldError`). The eager getter must therefore receive an *adapting* body (read the Provider,
`getOrElse`), and the generator cannot see — let alone trust — an instrumentation-provided body, so it
has to own that body itself.

**Conclusion.** Keeping the overridden getter concrete is fundamentally incompatible with the class
generator. The getter must be **abstract** so the generator can (a) claim the property as managed and
(b) own the eager-shim body. The in-place rewrite was a dead end.

## Chosen approach: strip the override to an abstract getter, let the generator own the body

Two coordinated layers; the eager getter is made **abstract** so the existing managed-property machinery
applies.

### 1. Instrumentation — `InstrumentingClassTransform`

Detect a pure `super`-delegating override of a replaced accessor and **strip it to an abstract method**:
drop the body, keep the signature and (crucially) the annotations. The two getters then look like:
`abstract int getMaxErrors()` (legacy) + `abstract Property<Integer> getMaxErrors()` (upgraded).

- Detection of pure super-delegation across compilers (JVM `INVOKESPECIAL`; statically-compiled Groovy;
  Kotlin, tolerating the trailing `Intrinsics.checkNotNullExpressionValue`; dynamic Groovy
  `ScriptBytecodeAdapter.invokeMethodOnSuper0`).
- "Is this a replaced accessor" comes from `isReplacedAccessor(owner, name, descriptor)` on the
  interceptor, generated for `BYTECODE_UPGRADE` interceptors.
- **Works for non-abstract plugin classes too** — drop the `isAbstractClass` guard. At the bytecode
  level a non-abstract class may declare an abstract method (the class-file format only constrains the
  method's own flags, not the enclosing class). Such a class loads fine; the generated
  `*_Decorated` subclass implements the method, and Gradle only ever instantiates the decorated
  subclass, so the abstract method is never invoked unimplemented. The generator already tolerates this
  shape (`assertNotAbstract` ignores abstract methods on non-abstract classes — "some other tooling
  (e.g. the Groovy compiler) has decided this is ok").
- Bump `DECORATION_FORMAT` (produced bytecode changes).

### 2. Class generator — `AbstractClassGenerator`

- `addGetter`: when neither getter return type is assignable to the other, **prefer the upgraded
  `Property`/`Provider` getter** as `mainGetter` over the legacy eager one. This types the backing field
  as the `Provider` and routes the property into the read-only managed bucket. (Covariant upgrades such
  as `FileCollection` <- `ConfigurableFileCollection` already pick the specialized type and work.)
- `ManagedPropertiesHandler` read-only loop: for the **eager-shim getter** — a getter whose return type
  is not assignable from the `Provider` field type but matches the scalar `Property<E>` element type
  (allowing for boxing) — generate it via a new `applyEagerShimGetter`; all other getters keep going
  through `applyReadOnlyManagedStateToGetter`.
- Add `isEagerShimGetter(property, getter)` + helpers (`scalarPropertyElementType`, `matchesWithBoxing`).

### 3. Bytecode — `AsmBackedClassGenerator.applyEagerShimGetter`

Emit `public <legacyType> <getter>() { return unpack(this.<getter>()); }`: load `this`, call the
**upgraded** getter virtually (lazily attaches the backing field), then unwrap to the legacy type. Using
a virtual call on `this` (not `super`) is required because the super accessor is abstract; targeting the
`Provider`-returning descriptor avoids recursion with the legacy getter.

### 4. Unwrap helper — `ManagedObjectFactory.unpackEagerShim`

A single helper, reused for every primitive/reference legacy type:

```java
@Nullable
public static Object unpackEagerShim(Provider<?> provider, Class<?> legacyType) {
    Object value = provider.getOrNull();
    return value != null ? value : Defaults.defaultValue(legacyType); // boxed zero / null
}
```

The generated getter unboxes (primitive) or casts (reference) the result. This honors a set value or
convention at call time; the JVM-zero default applies only when the property is truly absent — matching
the pre-upgrade eager semantics.

## Tests

- **Cross-version integration** (`PropertyUpgradesBinaryCompatibilityCrossVersionSpec`, target 8.0.2):
  override scenarios for the primitive (`int getMaxErrors`) and reference (`FileCollection
  getCheckstyleClasspath`) upgrades across Java, Groovy (statically + dynamically compiled), and Kotlin.
  Add **non-abstract** `CustomCheckstyle` variants to cover the dropped `isAbstractClass` guard.
  (Requires the two-arg `prepare*PluginTest(applyBody, additionalClasses)` helpers.)
- **Annotation metadata** (`DefaultTypeAnnotationMetadataStoreTest`): a subclass re-annotating the
  accessor (`@CompileClasspath` over inherited `@Classpath`, same `NORMALIZATION` category) resolves to
  the subclass annotation — confirms re-annotation survives the strip (the abstract method keeps its
  annotations).
- **Generator unit coverage** for `applyEagerShimGetter` if feasible without the cross-version harness.

## Validation

- In-sandbox: module compiles and the unit tests above (no Gradle daemon forking).
- The cross-version integration tests **cannot run green in the Claude sandbox** (forked daemons hit
  `ps`, FSEvents file-watching, and a Kotlin-compiler temp-lock). They are authored + compile-checked
  here; the authoritative run is `./gradlew :integ-test:gradle8.0.2CrossVersionTest --tests
  "*PropertyUpgradesBinaryCompatibilityCrossVersionSpec"` in a normal environment.

## Known limitations / follow-ups

- **Scalar `Property<E>` only.** `ListProperty`/`SetProperty`/`MapProperty` eager shims (List/Set/Map)
  are left on the existing path; element typing and empty-vs-null defaults are undecided.
- **Non-pure overrides** (e.g. `return super.getMaxErrors() * 2`) are out of scope — they are not pure
  super-delegation, so they are not stripped and remain unsupported.
- **Undecorated instantiation.** If the bare plugin class is instantiated without Gradle's decoration
  and the stripped getter is called, it throws `AbstractMethodError`. Tasks are always decorated, so this
  is an edge case; optionally the class could be flipped to `ACC_ABSTRACT` for fail-fast at the cost of
  breaking any legitimate direct `new`.
- **`addGetter` blast radius.** Preferring the `Provider` getter changes the backing-field type for any
  property that has both a `Provider` getter and an unrelated same-named getter — rare outside upgrades,
  but worth a wider decorated-types check before merge.
