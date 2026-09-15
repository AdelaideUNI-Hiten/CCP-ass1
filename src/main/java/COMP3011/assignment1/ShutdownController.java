package COMP3011.assignment1;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShutdownController {

    private final ApplicationContext context;
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public ShutdownController(ApplicationContext context) {
        this.context = context;
    }
    
    /*
     * 1. Atomically check if a shutdown is already in progress (via AtomicBoolean compareAndSet)
     * 2. If yes, return 409 Conflict with an ErrorResponse body
     * 3. If no, flip the flag to true and proceed
     * 4. Spin up a separate thread to handle the actual shutdown
     * 5. On that thread: sleep 500ms so the HTTP response has time to flush back to the client
     * 6. Call SpringApplication.exit() to gracefully close Spring's context/beans
     * 7. Call System.exit() with the returned code to actually terminate the JVM
     * 8. Meanwhile, immediately return 202 Accepted to the caller (doesn't wait for the thread)
     */
    @PostMapping("/api/v1/admin/shutdown")
    public ResponseEntity<?> shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            ErrorResponse error = new ErrorResponse(
                Instant.now().toString(),
                409,
                "Conflict",
                "Graceful shutdown is already in progress.",
                "/api/v1/admin/shutdown"
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            int exitCode = SpringApplication.exit(context, () -> 0);
            System.exit(exitCode);
        }).start();

        return ResponseEntity.accepted().body(new ShutdownResponse("Graceful shutdown requested."));
    }

    public record ShutdownResponse(String message) {}
}