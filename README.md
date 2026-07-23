# nyora-java

The **Nyora sources SDK for the JVM** — the native kotatsu-parsers engine (960+
manga / manhwa / manhua sources) as an **in-process library**. No HTTP server,
no cloud: you call the parsers directly from Java or Kotlin.

This is the JVM sibling of the [`nyora`](https://pypi.org/project/nyora/) (Python)
and `nyora-sdk` (JS) clients. Those talk to a helper over REST; on the JVM you
don't need to — the engine *is* JVM code, so this SDK links it straight in.

Requires Java 17+.

## Install

Published to **GitHub Packages**. Add the repo (GitHub Packages Maven requires a
token — even for public packages — so supply a PAT with `read:packages`), plus
jitpack for the engine's transitive `kotatsu-parsers` dependency:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/Nyora-Manga/nyora-java")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("com.nyora:nyora-java:2.1.0")
}
```

Maven — same repo + credentials in `~/.m2/settings.xml`, then:

```xml
<dependency>
  <groupId>com.nyora</groupId>
  <artifactId>nyora-java-jvm</artifactId>
  <version>2.1.0</version>
</dependency>
```

**No-auth alternative — build from source** (below): `./gradlew publishToMavenLocal`
puts `com.nyora:nyora-java:2.1.0` in your `~/.m2` for local consumers.

## Use (Java)

```java
NyoraSources nyora = NyoraSources.create();

for (MangaSource s : nyora.catalog()) System.out.println(s.getId());

MangaSearchPage page = nyora.popular("parser:MANGADEX", 1);
Manga first = page.getEntries().get(0);

MangaDetails d = nyora.details("parser:MANGADEX", first.getUrl());
List<MangaPage> pages = nyora.pages("parser:MANGADEX", d.getChapters().get(0));
```

## Use (Kotlin)

```kotlin
val nyora = NyoraSources()
val page = nyora.popular("parser:MANGADEX")
val details = nyora.details("parser:MANGADEX", page.entries.first().url)
val pages = nyora.pages("parser:MANGADEX", details.chapters.first())
```

## API

`NyoraSources` (construct once, reuse — parser services are cached):

| Method | Returns |
| --- | --- |
| `catalog()` | every `MangaSource` the engine can parse |
| `sourceById(id)` / `findSource(query)` | look up a source |
| `popular(id, page=1)` | `MangaSearchPage` |
| `latest(id, page=1)` | `MangaSearchPage` |
| `search(id, query, page=1, filters=[])` | `MangaSearchPage` |
| `details(id, url)` | `MangaDetails` (manga + chapters) |
| `pages(id, chapter)` | `List<MangaPage>` |
| `filters(id)` | advertised search filters |
| `supportsLatest(id)`, `imageHeaders(id)` | source capabilities |

The browse/details/pages calls are **blocking** (they wrap the engine's
coroutine API) — call them off your UI thread.

> Source ids are `parser:`-prefixed, e.g. `parser:MANGADEX`, `parser:MANGAFIRE`.

## Fat jar

`./gradlew sdkJar` produces `build/libs/nyora-java-<version>-all.jar` — the SDK,
engine, and all dependencies in one jar. It's also runnable as the standalone
Nyora helper server (`Main-Class` = `HelperMain`), so the same artifact doubles
as `nyora-extension-server`'s engine.

## Build

```bash
./gradlew publishToMavenLocal   # install to ~/.m2 for local consumers
./gradlew sdkJar                # fat jar
```

Engine source is the sibling `../nyora-shared` checkout (compiled via `srcDirs`).

## License

Apache-2.0.
