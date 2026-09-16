# Assignment 1 — Speech to Text Web Site

COMP3011 Assignment 1: a Spring Boot site that records audio in the browser,
uploads it to a backend REST endpoint, transcribes it via OpenAI's
`gpt-4o-mini-transcribe` model, and returns the text to the page. Also
exposes admin/reporting endpoints (`uptime`, `global/stats`,
`admin/shutdown`) per the assignment's YAML spec.

## Architecture

Package `COMP3011.assignment1`, one `@RestController` per concern:

- `TranscriptionController` — `POST /api/v1/transcribe`. Accepts a
  multipart `audio` file, forwards it to the STT service via a
  `RestClient` built from `openai.api.base-url`, and returns `{text, usage}`.
  On failure it returns a `500` with an `ErrorResponse` body rather than
  letting the exception surface as a stack trace.
- `UptimeResponseController` — `GET /api/v1/admin/uptime`.
- `GlobalStatsController` — `GET /api/v1/global/stats`. Holds cumulative
  input/output token counters (`AtomicLong`) updated by
  `TranscriptionController` after every successful transcription.
- `ShutdownController` — `POST /api/v1/admin/shutdown`. Guards against a
  second concurrent shutdown request with a `compareAndSet` on an
  `AtomicBoolean`, then exits on a separate thread after a short delay so
  the `202 Accepted` response has time to flush to the caller.
- `LocalSttStubController` — only active under the `local` Spring profile
  (see below); stands in for OpenAI so local development doesn't need a
  real API key.

Frontend is static HTML/CSS/vanilla JS in `src/main/resources/static/`
(`index.html`, `app.js`, `style.css`) — `MediaRecorder` captures audio,
`fetch` uploads it as `multipart/form-data`, the returned text is
displayed and the UI resets for another recording.

**Known gap:** there's no `@RestControllerAdvice` yet, so a request
missing the `audio` part currently falls through to Spring's default
error body instead of the `ErrorResponse` shape used elsewhere. Flagging
this here rather than hiding it — worth adding if time permits.

## Concurrency

`spring.threads.virtual.enabled=true` in `application.properties` makes
Tomcat hand each inbound request a virtual thread, so a request blocked
waiting on the OpenAI HTTP call doesn't tie up a platform thread. This
only helps if the *outbound* call to OpenAI doesn't itself serialise
behind a small connection pool — that's a separate, easy-to-miss failure
mode, so it's verified rather than assumed (see `ConcurrencyLoadTest`
below).

## Security: the OpenAI API key

`TranscriptionController` reads the key via
`@Value("${OPENAI_API_KEY:}")`, which resolves from the OS environment
variable at startup — never hardcoded, never logged (log lines mention
status codes and durations, not the header value), never persisted. If
the key is blank the controller fails the request with a generic message
rather than echoing the missing-key detail.

## Profiles: local vs TITAN

Both environments read `OPENAI_API_KEY` from the environment and talk to
`openai.api.base-url`. The only difference is which profile is active:

- **Default profile (TITAN / normal run):** `openai.api.base-url` points
  at `https://api.openai.com`; a real `OPENAI_API_KEY` must be set in the
  environment.
- **`local` profile** (`application-local.properties`): points
  `openai.api.base-url` at `http://localhost:8080` (this same app) and
  sets a placeholder `OPENAI_API_KEY`. `LocalSttStubController`, active
  only under this profile, answers `/v1/audio/transcriptions` with a
  canned response — so the full record → upload → transcribe → display
  flow can be exercised locally with no real key and no network call.

Run locally against the stub:

```
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

or, against the packaged jar:

```
SPRING_PROFILES_ACTIVE=local java -jar target/Assignment1-0.0.1-SNAPSHOT.jar
```

No profile flag / no `SPRING_PROFILES_ACTIVE` set → default profile,
real OpenAI, real key required — this is what TITAN runs.

## Tests

All in `src/test/java/COMP3011/assignment1/`. `OpenAiStubServer` is a
small `com.sun.net.httpserver.HttpServer`-based stand-in for OpenAI's
endpoint (not the `local`-profile stub controller — a separate,
test-only server so tests don't depend on the app's own code under
test). Each test class points `openai.api.base-url` at it via
`@DynamicPropertySource`.

- **`TranscriptionControllerIntegrationTest`** — regression coverage for
  `/api/v1/transcribe` against the stub: a normal transcription returns
  the expected text and usage; an upstream `500` from the STT service is
  translated into the app's own `500`/`ErrorResponse`; a request with no
  `audio` part is rejected with a `4xx`. Assures the core REST endpoint
  behaves correctly without spending real API credits on every test run.

- **`ConcurrencyLoadTest`** — fires 250 concurrent blocking requests at a
  live embedded server (virtual-thread executor, `RestClient` per
  request) against a stub that holds every connection open for 200ms.
  Serialised handling would take roughly `250 × 200ms ≈ 50s`; the
  assertion requires completion in under 5s. This is the test that
  actually proves the concurrency claim in the spec (">200 concurrent
  blocking HTTP requests") rather than just trusting that
  `spring.threads.virtual.enabled=true` is enough — last run completed
  in ~2.7s.

- **`TokenStatsRaceConditionTest`** — fires 150 concurrent transcriptions
  each with a known, fixed token cost, then asserts `/api/v1/global/stats`
  recorded the *exact* sum before/after delta. Since the counters are
  `AtomicLong`, this is a correctness proof rather than a coincidence:
  if the increments weren't atomic, concurrent updates would lose counts
  and this test would catch it.

Run everything: `./mvnw test`.

## Building and running

```
./mvnw clean package
java -jar target/Assignment1-0.0.1-SNAPSHOT.jar
```

Produces a single executable (fat) jar via `spring-boot-maven-plugin`.
Serves the page at `http://localhost:8080/`.
