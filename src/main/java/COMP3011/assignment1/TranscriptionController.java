package COMP3011.assignment1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class TranscriptionController {
    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/api/v1/transcribe")
    public ResponseEntity<TranscriptionResponse> transcribe(@RequestParam("audio") MultipartFile audioFile) throws IOException {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioFile.getBytes()) {
            @Override
            public String getFilename() {
                return audioFile.getOriginalFilename();
            }
        });
        body.add("model", "gpt-4o-mini-transcribe");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<TranscriptionResponse> response = restTemplate.postForEntity(
            "https://api.openai.com/v1/audio/transcriptions",
            requestEntity,
            TranscriptionResponse.class
        );

        TranscriptionResponse result = response.getBody();
        if (result != null && result.usage() != null) {
            GlobalStatsController.recordUsage(result.usage().input_tokens(), result.usage().output_tokens());
        }

        return ResponseEntity.ok(result);
    }

    public record TranscriptionResponse(String text, Usage usage) {
        public record Usage(String type, long input_tokens, long output_tokens, long total_tokens) {}
    }
}
