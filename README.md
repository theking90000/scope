# scope

> First-class scopes for the JVM: create, nest, shadow and dispose
> runtime scopes — with automatic dependency injection.

`scope` models **scopes as first-class runtime objects**. Scopes are built,
nested, overridden and disposed while the program runs — the way lexical blocks
behave in JavaScript or Rust, but reshaped at runtime instead of fixed at compile
time. A small dependency injection layer resolves dependencies along the scope
graph.

> Manage lifetimes and visibility at runtime — plugin, player, game, action — as
> nestable scopes with automatic wiring.

It is built for systems that juggle many overlapping lifetimes and visibilities
at once. On a game server like Minecraft, the same value can live at the scale of
the plugin, a player, a running game, or a single action — each with its own
boundary of visibility, state and lifecycle. `scope` models each of those as a
scope, and resolves dependencies along the scope graph.

## The mental model: scopes as language blocks

A `Scope` reads like a lexical block in JavaScript, Rust or Java. A value defined
in an outer block is visible to inner blocks, unless an inner block defines a
nearer value under the same key — in which case the nearer one shadows it.

```text
// RootScope
{
    x = value;

    // PlayerScope
    {
        player = value;
        classes = ...;

        // An object created here sees:
        // - player and classes locally;
        // - x from the parent;
        // - any local definition shadowing a parent definition.
    }

    // GameScope
    {
        game = value;
        players = List<Scope<Player>>;

        // Player1Scope
        { player = player1; }

        // Player2Scope
        { player = player2; }
    }
}
```

The difference with the compiler's version: these blocks are **objects**. They
are opened, nested across multiple parents, shadowed and closed at runtime —
not fixed at compile time. Default resolution uses the **nearest** definition, so
a local value shadows a parent's value exactly like a local variable.

## What makes it different

Most DI frameworks treat scoping as a feature bolted onto a container. Here the
scope **is** the model, and dependency injection is just what falls out of it.

1. **The scope is the feature, not a bonus.**
   There is no `@Singleton`, no `@RequestScoped`, no fixed zoo of predefined
   scopes. There is one concept — the scope — and **everything is a singleton
   within its scope**. Scopes are made to be created, nested, manipulated and
   closed on the fly, not declared once at startup.

2. **An API kept deliberately far from the domain.**
   A `Scope` knows nothing about annotations, classloaders, "events" or
   "requests". It knows **nested spaces of variable visibility**, nothing more.
   Domain vocabulary — player, game, plugin — stays in application code, layered
   on top.

3. **Lightweight, dynamic DI.**
   No heavy machinery: it resolves a constructor from what is present in the
   environment, walking up the scope graph, and **instantiates in the current
   scope when nothing is found** along the way. That is the whole wiring story.

4. **`close()` is at the core, not an afterthought.**
   Closing a scope releases all of its resources **deterministically and on
   purpose**, so domain logic runs _before_ the GC ever sees the objects
   (`@PreDestroy`, `AutoCloseable`, disposers). Timing and order are explicit
   (LIFO) — release does not wait on the collector.

5. **Extension points designed to be shadowed.**
   `OnCreatedHook`s are not a frozen global mechanism: they follow the **same
   shadowing rules** as everything else, so a child scope can override or rewrite
   a hook locally — again with deterministic cleanup through disposers.

## How it compares

|                                                            |    Guice     |  Avaje  | ActiveJ |  tiko-di   |  **scope**  |
| ---------------------------------------------------------- | :----------: | :-----: | :-----: | :--------: | :---------: |
| Hierarchical scopes                                        |      ✅      |   ✅    |   ✅    | ⚠️ 3 fixed |     ✅      |
| **Multi-parent (DAG)**                                     |      ❌      |   ❌    |   ❌    |     ❌     |     ✅      |
| **Lexical shadowing** (nearest definition wins)            | ❌ forbidden |   ❌    |   ❌    |     ❌     |     ✅      |
| **Runtime-manipulable scope graph** (create/drop/override) |      ❌      |   ❌    |   ❌    |     ❌     |     ✅      |
| **Scope _is_ the model** (DI is a consequence)             |      ❌      |   ❌    |   ❌    |     ❌     |     ✅      |
| **No "singleton" notion** (everything is scope-singleton)  |      ❌      |   ❌    |   ❌    |     ❌     |     ✅      |
| Deterministic `close()` (domain cleanup before GC)         |      ❌      |   ✅    |   ⚠️    |     ✅     |     ✅      |
| **Domain-agnostic API** (no annotations / classloaders)    |      ❌      |   ❌    |   ❌    |     ❌     |     ✅      |
| Resolution                                                 |   runtime    | compile | runtime |  compile   | **runtime** |

The differentiator is not any single cell — it is having all of them at once:
**a multi-parent, lexically-shadowed scope graph you reshape at runtime, scope-first.**

## Hello, scope

```java
import be.theking90000.scope.Scope;

record RootScope() {}
record Config(String value) {}
record Service(Config config) {}

Scope<RootScope> root = new Scope<>(new RootScope());
root.seed(Config.class, new Config("prod"));

// Service has no provider, so the container builds it:
// it inspects the single public constructor, resolves Config,
// instantiates Service(config) and caches it as a scope singleton.
Service service = root.get(Service.class);
```

Scopes nest and close deterministically:

```java
record Player(String name) {}
record Session(Player player, RootScope root) {}

Scope<Player> player = new Scope<>(new Player("Ada"));
player.ownedBy(root);                       // sees root; root owns and will close it

Session session = player.get(Session.class); // Player from here, RootScope from parent

player.close();                              // runs @PreDestroy / AutoCloseable / disposers, LIFO
```

## Quick start

The library targets **Java 21** and is published to **GitHub Packages**. GitHub
Packages requires authentication for reads, so a token is needed (a classic PAT
with the `read:packages` scope is enough).

> Coordinates are `be.theking90000:scope:1.0.0`, published under the
> `theking90000/scope` repository. The artifact is not published yet — build from
> source in the meantime.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/theking90000/scope")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("be.theking90000:scope:1.0.0")
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/theking90000/scope</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>be.theking90000</groupId>
    <artifactId>scope</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```

Credentials go in `~/.m2/settings.xml` under a matching `<server><id>github</id></server>`.

### Build from source

```bash
./gradlew :scope:build
./gradlew :scope:test
```

## Documentation

The hosted docs are available at **<https://theking90000.github.io/scope/>**.

The full English guide lives in **[`docs/`](docs/README.md)** — start with the
[mental model](docs/mental-model.md), then the
[API reference](docs/api-reference.md), [extension hooks](docs/extension-hooks.md),
[multi-parent scopes](docs/multi-parent.md) and [lifecycle](docs/scopes-and-lifecycle.md).

Every public type ships with thorough JavaDoc:
**<https://theking90000.github.io/scope/javadoc/>**.

The original in-depth reference, in French, is
[`scope/README.md`](scope/README.md).
