package COMP3011.assignment1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fires many concurrent transcriptions, each with a known token cost, and
 * checks the global counters record the exact sum with no lost updates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TokenStatsRaceConditionTest {

    private static final int CONCURRENT_REQUESTS = 150;
    private static final long INPUT_TOKENS_PER_CALL = 7;
    private static final long OUTPUT_TOKENS_PER_CALL = 3;

    private static final OpenAiStubServer STUB = new OpenAiStubServer();

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        STUB.setResponse(200, "{\"text\":\"race\",\"usage\":{\"input_tokens\":" + INPUT_TOKENS_PER_CALL
                + ",\"output_tokens\":" + OUTPUT_TOKENS_PER_CALL + "}}");
        registry.add("openai.api.base-url", () -> "http://localhost:" + STUB.port());
        registry.add("OPENAI_API_KEY", () -> "test-key");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Test
    void concurrentTranscriptionsRecordExactTokenTotals() throws Exception {
        GlobalStatsController.GlobalStatsResponse before = restTemplate.getForObject(
                "/api/v1/global/stats", GlobalStatsController.GlobalStatsResponse.class);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ResponseEntity<String>>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(i -> executor.submit(() -> restTemplate.postForEntity(
                            "/api/v1/transcribe", buildAudioRequest(), String.class)))
                    .toList();

            for (Future<ResponseEntity<String>> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        GlobalStatsController.GlobalStatsResponse after = restTemplate.getForObject(
                "/api/v1/global/stats", GlobalStatsController.GlobalStatsResponse.class);

        assertThat(after.inputTokens() - before.inputTokens())
                .isEqualTo(CONCURRENT_REQUESTS * INPUT_TOKENS_PER_CALL);
        assertThat(after.outputTokens() - before.outputTokens())
                .isEqualTo(CONCURRENT_REQUESTS * OUTPUT_TOKENS_PER_CALL);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildAudioRequest() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", new ByteArrayResource(new byte[]{9, 9, 9}) {
            @Override
            public String getFilename() {
                return "recording.webm";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }
}
