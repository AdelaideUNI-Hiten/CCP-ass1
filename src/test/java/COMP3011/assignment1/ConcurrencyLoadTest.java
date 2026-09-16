package COMP3011.assignment1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the backend can service 200+ overlapping blocking HTTP requests
 * concurrently rather than queueing them one at a time behind the STT call.
 * The stub STT service holds every request open for STT_DELAY_MILLIS; a
 * serialised implementation would take CONCURRENT_REQUESTS * that delay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyLoadTest {

    private static final int CONCURRENT_REQUESTS = 250;
    private static final long STT_DELAY_MILLIS = 200;

    private static final OpenAiStubServer STUB = new OpenAiStubServer();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        STUB.setDelayMillis(STT_DELAY_MILLIS);
        registry.add("openai.api.base-url", () -> "http://localhost:" + STUB.port());
        registry.add("OPENAI_API_KEY", () -> "test-key");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Test
    void handlesTwoHundredFiftyConcurrentBlockingRequestsWithoutSerialising() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        AtomicInteger successCount = new AtomicInteger();

        Callable<Void> task = () -> {
            if (requestOnce(client).is2xxSuccessful()) {
                successCount.incrementAndGet();
            }
            return null;
        };

        Instant start;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            start = Instant.now();

            List<Future<Void>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(i -> executor.submit(task))
                    .toList();

            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(successCount.get()).isEqualTo(CONCURRENT_REQUESTS);
        // Fully serialised handling would take ~250 * 200ms = 50s; real
        // concurrency keeps this close to a single round trip.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    private HttpStatusCode requestOnce(RestClient client) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", new ByteArrayResource(new byte[]{1, 2, 3, 4}) {
            @Override
            public String getFilename() {
                return "recording.webm";
            }
        });

        return client.post()
                .uri("/api/v1/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }
}
