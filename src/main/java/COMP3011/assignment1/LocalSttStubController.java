package COMP3011.assignment1;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stand-in for OpenAI's /v1/audio/transcriptions endpoint, active only under
 * the "local" Spring profile (see application-local.properties). Lets the
 * full record-upload-transcribe-display flow be exercised on a developer
 * machine without a real OPENAI_API_KEY or network call.
 */
@Profile("local")
@RestController
public class LocalSttStubController {

    @PostMapping(value = "/v1/audio/transcriptions", produces = MediaType.APPLICATION_JSON_VALUE)
    public StubResponse transcribe(@RequestParam("file") MultipartFile file) {
        String text = "Local stub transcription of '%s' (%d bytes). This is the 'local' Spring profile talking; run with the default profile and a real OPENAI_API_KEY to reach the real OpenAI service."
                .formatted(file.getOriginalFilename(), file.getSize());
        return new StubResponse(text, new StubResponse.Usage(0, 0));
    }

    public record StubResponse(String text, Usage usage) {
        public record Usage(
                @JsonProperty("input_tokens") long inputTokens,
                @JsonProperty("output_tokens") long outputTokens) {}
    }
}
