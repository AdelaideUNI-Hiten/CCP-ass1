package COMP3011.assignment1;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GlobalStatsController {

    private static final AtomicLong inputTokens = new AtomicLong(0);
    private static final AtomicLong outputTokens = new AtomicLong(0);

    @GetMapping("/api/v1/global/stats")
    public GlobalStatsResponse getGlobalStats() {
        return new GlobalStatsResponse(inputTokens.get(), outputTokens.get());
    }

    // Call this from your OpenAI-calling endpoint later, once each transcription completes
    public static void recordUsage(long input, long output) {
        inputTokens.addAndGet(input);
        outputTokens.addAndGet(output);
    }

    public record GlobalStatsResponse(
        long inputTokens,
        long outputTokens
    ) {}
}
