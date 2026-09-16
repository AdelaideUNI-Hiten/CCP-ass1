package COMP3011.assignment1;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@RestController
public class TranscriptionController {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionController.class);
    private static final String MODEL = "gpt-4o-mini-transcribe";

    private final RestClient restClient;
    private final String apiKey;

    public TranscriptionController(RestClient.Builder restClientBuilder,
                                    @Value("${openai.api.base-url}") String baseUrl,
                                    @Value("${OPENAI_API_KEY:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @PostMapping("/api/v1/transcribe")
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio) {
        try {
            TranscriptionResponse result = callOpenAi(audio);
            GlobalStatsController.recordUsage(result.usage().input_tokens(), result.usage().output_tokens());
            return ResponseEntity.ok(result);

        } catch (TranscriptionException ex) {
            log.warn("Transcription failed: {}", ex.getMessage());
            return errorResponse(500, "Internal Server Error", ex.getMessage());
        }
    }

    private TranscriptionResponse callOpenAi(MultipartFile audio) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OPENAI_API_KEY is not set; cannot call transcription service.");
            throw new TranscriptionException("The speech-to-text service is not available right now.");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", MODEL);
        body.add("response_format", "json");
        body.add("file", audio.getResource());

        try {
            long start = System.currentTimeMillis();

            OpenAiTranscriptionResponse parsed = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OpenAiTranscriptionResponse.class);

            long elapsedMs = System.currentTimeMillis() - start;

            OpenAiTranscriptionResponse.Usage usage = parsed.usage() != null
                    ? parsed.usage()
                    : new OpenAiTranscriptionResponse.Usage(0, 0);

            log.info("Transcription succeeded in {} ms (inputTokens={}, outputTokens={})",
                    elapsedMs, usage.inputTokens(), usage.outputTokens());

            return new TranscriptionResponse(parsed.text(),
                    new TranscriptionResponse.Usage(usage.inputTokens(), usage.outputTokens()));

        } catch (HttpStatusCodeException ex) {
            HttpStatusCode status = ex.getStatusCode();
            log.warn("OpenAI transcription call failed with status {}: {}", status, ex.getResponseBodyAsString());
            throw new TranscriptionException(
                    "The speech-to-text service could not process this request.", ex);
        } catch (ResourceAccessException ex) {
            log.warn("OpenAI transcription call failed: network error", ex);
            throw new TranscriptionException(
                    "The speech-to-text service is currently unreachable.", ex);
        }
    }

    private ResponseEntity<ErrorResponse> errorResponse(int status, String error, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(new ErrorResponse(
                Instant.now().toString(), status, error, message, "/api/v1/transcribe"
        ));
    }

    private record OpenAiTranscriptionResponse(String text, Usage usage) {
        private record Usage(
                @JsonProperty("input_tokens") long inputTokens,
                @JsonProperty("output_tokens") long outputTokens) {}
    }

    private static class TranscriptionException extends RuntimeException {
        TranscriptionException(String message) {
            super(message);
        }

        TranscriptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record TranscriptionResponse(String text, Usage usage) {
        public record Usage(long input_tokens, long output_tokens) {}
    }
}
