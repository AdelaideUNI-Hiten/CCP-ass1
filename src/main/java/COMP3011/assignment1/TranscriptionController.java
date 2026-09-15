package COMP3011.assignment1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
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

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    private final RestClient restClient = RestClient.create("https://api.openai.com/v1/audio/transcriptions");

    @PostMapping("/api/v1/transcribe")
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio) {

        if (apiKey == null || apiKey.isBlank()) {
            return errorResponse(500, "Internal Server Error", "The speech-to-text service is not configured correctly.");
        }

        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", audio.getResource());
            body.part("model", "gpt-4o-mini-transcribe");

            TranscriptionResponse result = restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(h -> h.setBearerAuth(apiKey))
                .body(body.build())
                .retrieve()
                .body(TranscriptionResponse.class);

            if (result != null && result.usage() != null) {
                GlobalStatsController.recordUsage(result.usage().input_tokens(), result.usage().output_tokens());
            }

            return ResponseEntity.ok(result);

        } catch (HttpStatusCodeException ex) {
            return errorResponse(500, "Internal Server Error", "The speech-to-text service could not process this request.");

        } catch (ResourceAccessException ex) {
            return errorResponse(500, "Internal Server Error", "The speech-to-text service is currently unreachable.");

        } catch (Exception ex) {
            return errorResponse(500, "Internal Server Error", "An unexpected error occurred during transcription.");
        }
    }

    private ResponseEntity<ErrorResponse> errorResponse(int status, String error, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(new ErrorResponse(
                Instant.now().toString(), status, error, message, "/api/v1/transcribe"
        ));
    }

    public record TranscriptionResponse(String text, Usage usage) {
        public record Usage(String type, long input_tokens, long output_tokens, long total_tokens) {}
    }
}