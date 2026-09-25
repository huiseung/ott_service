package com.domain.backend.analyticsjob;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.flink.configuration.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClickHouseBatchSinkTest {
    private HttpServer server;
    private final List<String> bodies = new ArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private ClickHouseBatchSink sink;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        sink = new ClickHouseBatchSink("http://127.0.0.1:" + server.getAddress().getPort(), 2);
        sink.open(new Configuration());
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void batchesAndFlushesLowTrafficBeforeCheckpoint() throws Exception {
        sink.invoke("{\"event\":1}", null);
        assertEquals(0, bodies.size());
        sink.invoke("{\"event\":2}", null);
        assertEquals(1, bodies.size());
        assertTrue(bodies.get(0).endsWith("{\"event\":1}\n{\"event\":2}\n"));
        sink.invoke("{\"event\":3}", null);
        sink.snapshotState(null);
        assertEquals(2, bodies.size());
        sink.snapshotState(null);
        assertEquals(2, bodies.size());
    }

    @Test void failedInsertFailsCheckpointAndDoesNotDiscardBatch() throws Exception {
        sink.invoke("{\"event\":1}", null);
        status.set(500);
        assertThrows(IOException.class, () -> sink.snapshotState(null));
        status.set(200);
        sink.snapshotState(null);
        assertEquals(2, bodies.size());
        assertEquals(bodies.get(0), bodies.get(1));
    }
}
