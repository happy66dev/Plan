# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Plan (Player Analytics) is a Minecraft server analytics plugin (LGPL-3). A single jar supports Bukkit/Spigot/Paper/Folia, BungeeCord, Velocity, Sponge, Nukkit and Fabric. It runs a built-in Jetty webserver (serving a React/Vite dashboard) that shows player, online-activity and performance analytics, plus an extension API so other plugins can add custom data.

The Gradle project root is the `Plan/` subdirectory — not the git repo root. Run all Gradle commands from there. `versions.txt` at the repo root maps version → download URL and is used by the in-game update checker (`common/.../version/VersionChecker`).

## Build & test

Java 25 toolchain required (Gradle auto-provisions it). Source compatibility per module: `api` → `--release 8`, `common` main → `--release 11`, everything else → `--release 25`.

```bash
cd Plan
./gradlew build                    # assemble + check (tests + checkstyle) + react bundle + shadow jars
./gradlew build -x test            # skip tests (checkstyle still runs) — matches CI's first build step
./gradlew test                     # all tests (JUnit 5)
./gradlew test --tests "com.djrapitops.plan.storage.database.queries.PlayerFetchQueriesTest"  # one test class
./gradlew check                    # checkstyle + tests
./gradlew aggregateJavadocs        # combined javadocs (deployed to gh-pages)
./gradlew bundle                   # :common task — rebuild the React dashboard
```

Build number is derived from `git rev-list --count HEAD`; `Plan` requires a full git history for the version to resolve.

Output jars land in `Plan/builds/`: `Plan-<version>.jar` (from `:plugin`) and `PlanFabric-<version>.jar` (from `:fabric`).

### Gotchas

- The React dashboard (`react/dashboard`, Vite + TS) is built by a Yarn task inside `:common`. The Gradle Node plugin downloads Node 24.15.0, and `processResources` depends on the bundle — so nearly any `:common` build/test needs network. Pass `-PisJitpack` to skip all Yarn steps (used by Jitpack, which only needs Java deps).
- Some `:common` tests need Docker (Testcontainers spins up MariaDB/MySQL) and CI additionally runs a MariaDB service + chromedriver/Selenium for full end-to-end. Expect some tests to fail/skip locally without Docker.
- `:common` tests set `PLAN_TEST_*` env vars in `test {}` and use `forkEvery = 100`.

## Module layout

| Module | Purpose |
|---|---|
| `api` | Public API for third-party plugins (`DataExtension`, `ExtensionService`, `PlanAPI` v5). Published as `plan-api`, compiles to Java 8. |
| `common` | Everything core: config, DB storage, gathering, delivery/webserver, commands, queries, extension runtime. Hosts `testFixtures` and the React bundle (served from `assets/plan/web`). |
| `extensions`, `extensions:adventure` | Optional API add-ons (adventure-component conversion). |
| `bukkit`, `bungeecord`, `velocity`, `sponge`, `nukkit`, `folia`, `fabric` | Thin per-platform adapters: implement `PlanPlugin`, define Dagger `@Module`s, hook platform events. `fabric` shades its own `PlanFabric-*.jar`. |
| `plugin` | No main code — shadows all platform + common modules into the fat `Plan.jar`. |
| `react/dashboard` | Frontend source, bundled into `common` resources. |

## Core architecture

### PlanSystem — central orchestrator (`common`)
`PlanSystem` is the DI-wired `@Singleton` everything hangs off. `enableOtherThanCommands()` enables `SubSystem`s in order — config → processing/files/locale/versionChecker → database → webserver → serverInfo → import/export → cache → listeners → tasks — and `disable()` tears them down in reverse. `PlanPlugin` is the platform-agnostic lifecycle interface (`onEnable`/`onDisable`/`getSystem()`); each platform module implements it (e.g. `bukkit/.../Plan.java`). Wiring is Dagger everywhere: `@Inject` constructors, `@Singleton`, per-platform `modules` packages. In the shadow jar Dagger is relocated to `plan.dagger`.

### Data layer (`common/storage/database`)
- `DBSystem` → `Database`; `SQLDB` abstract base; `MySQLDB`/`SQLiteDB` (MariaDB included), connections via HikariCP, `DBType` enum. DB driver jars download at runtime (dependencydownload resources under `assets/plan/dependencies/`).
- Reads are `Query` objects (`QueryStatement`, `RowExtractor`, `queries/` subpackages `analysis`, `containers`, `filter`, `objects`, `schema`) run via `database.query(...)`.
- Writes are `Transaction` objects (`ExecStatement`/`ExecBatchStatement`, `transactions/` subpackages `commands`, `events`, `init`, `patches`) run via `database.executeTransaction(...)`. Schema migrations are transactions in `transactions/patches`, coordinated by `Patches`.

### DataSvc — in-memory data bus (`common`)
`DataSvc implements DataService`: a registry of pull sources, sinks and mappers keyed by `(identifier class, value class)` pairs. Producers `push(...)` values; consumers register callbacks via `registerPullSource` / `registerSink` / `registerMapper` (with database-backed variants that wrap `Query`/`Transaction`). This decouples listeners (producers) from web resolvers (consumers) without them knowing each other.

### QuerySvc — raw-SQL API for extensions
`QuerySvc implements QueryService` (public API): `query(sql, fn)` / `execute(sql, fn)` over `PreparedStatement`, subscribe hooks for player-remove / data-clear events, and `CommonQueries` for common SQL reads.

### Web server (`common/delivery/webserver`)
Jetty-based `WebServerSystem`. Requests flow through a `ResponseResolver` chain (per-route resolvers under `resolver/`, incl. `auth`, `json`, `swagger`), gated by `RateLimitGuard`, `PassBruteForceGuard`, and `CacheStrategy`/ETag. JSON endpoints (`/v1/*`) are Swagger-annotated; up-to-date endpoint docs are served at `/docs`. Static assets come from `assets/plan/web` (React build).

### Gathering (`common/gathering`)
Platform listeners (`gathering/listeners`) translate server events into data via `PlayerGatheringTasks`; timed tasks (`gathering/timed`, `ServerSensor`, `SystemUsage`) sample TPS/CPU/memory; `gathering/cache` keeps in-memory player session state. Results are pushed into `DataSvc` and persisted via transactions.

### Extension API (`api/extension`)
Third-party plugins register an annotation-driven `DataExtension` (`@Provider`, `@BooleanData`, ...) via `ExtensionService`; the runtime in `common/extension` extracts values, stores them, and surfaces them on player/server pages and API v5 endpoints.

## Testing

- Unit tests live in each module's `src/test` (JUnit 5 + Mockito). `:common` exposes `testFixtures` (`utilities/mocks`) that build a real Dagger graph for integration tests.
- `:common` tests exercise real DBs via Testcontainers; CI additionally provisions MariaDB + chromedriver for Selenium-driven page tests.
- Use standard Gradle `--tests` filtering to run a single test; `:common` tests require the React bundle to be built first (see Gotchas).
