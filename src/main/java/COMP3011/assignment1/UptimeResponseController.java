package COMP3011.assignment1;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UptimeResponseController {

    private static final Instant SERVER_START = Instant.now();

    @GetMapping("/api/v1/admin/uptime")
    public UptimeResponse getUptime() {
        Instant now = Instant.now();
        double uptimeSeconds = (now.toEpochMilli() - SERVER_START.toEpochMilli()) / 1000.0;
        return new UptimeResponse(SERVER_START.toString(), now.toString(), uptimeSeconds);
    }

    public record UptimeResponse(
        String utcServerStart,
        String utcNow,
        double serverUptimeSecond
    ) {}
}