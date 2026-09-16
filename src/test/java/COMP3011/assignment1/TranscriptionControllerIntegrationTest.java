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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for /api/v1/transcribe against a stub STT service, so
 * they run without a real OpenAI key or network call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TranscriptionControllerIntegrationTest {

    private static final OpenAiStubServer STUB = new OpenAiStubServer();

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("openai.api.base-url", () -> "http://localhost:" + STUB.port());
        registry.add("OPENAI_API_KEY", () -> "test-key");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Test
    void transcribeReturnsTextFromSttService() {
        STUB.setResponse(200, "{\"text\":\"hello world\",\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}");

        ResponseEntity<TranscriptionController.TranscriptionResponse> response = restTemplate.postForEntity(
                "/api/v1/transcribe", buildAudioRequest(new byte[]{1, 2, 3, 4}),
                TranscriptionController.TranscriptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().text()).isEqualTo("hello world");
        assertThat(response.getBody().usage().input_tokens()).isEqualTo(12);
        assertThat(response.getBody().usage().output_tokens()).isEqualTo(3);
    }

    @Test
    void transcribeReturns500WhenSttServiceFails() {
        STUB.setResponse(500, "{\"error\":\"boom\"}");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/transcribe", HttpMethod.POST,
                buildAudioRequest(new byte[]{1, 2, 3}), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isNotBlank();

        // restore default so later tests in this class see a healthy stub
        STUB.setResponse(200, "{\"text\":\"hello world\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}");
    }

    @Test
    void transcribeReturnsBadRequestWhenAudioPartMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(new LinkedMultiValueMap<>(), headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/transcribe", request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    private HttpEntity<MultiValueMap<String, Object>> buildAudioRequest(byte[] audioBytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", new ByteArrayResource(audioBytes) {
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
