package COMP3011.assignment1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
public class TranscriptionController {

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    private final RestClient restClient = RestClient.create("https://api.openai.com/v1/audio/transcriptions");

    @PostMapping("/api/v1/transcribe")
    public CompletableFuture<TranscriptionResponse> transcribe(@RequestParam("audio") MultipartFile audio) throws IOException {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", audio.getResource());
        body.part("model", "gpt-4o-mini-transcribe");

        return CompletableFuture.supplyAsync(() -> {
            TranscriptionResponse result = restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(body.build())
                .retrieve()
                .body(TranscriptionResponse.class);

            if (result != null && result.usage() != null) {
                GlobalStatsController.recordUsage(result.usage().input_tokens(), result.usage().output_tokens());
            }

            return result;
        });
    }

    public record TranscriptionResponse(String text, Usage usage) {
        public record Usage(String type, long input_tokens, long output_tokens, long total_tokens) {}
    }
}
