package COMP3011.assignment1;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for the OpenAI transcription endpoint used by tests, so no real
 * network call or API key is required. Tests point openai.api.base-url at
 * this server's port and control its canned response, delay and status.
 */
class OpenAiStubServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile long delayMillis = 0;
    private volatile int responseStatus = 200;
    private volatile String responseBody =
            "{\"text\":\"stub transcript\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

    OpenAiStubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.createContext("/v1/audio/transcriptions", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    int requestCount() {
        return requestCount.get();
    }

    void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    void setResponse(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        exchange.getRequestBody().readAllBytes();

        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
