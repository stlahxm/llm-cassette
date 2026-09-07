package dev.llmcassette;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Exercises the real {@code @RegisterExtension} wiring end-to-end (via the actual JUnit5
 * engine, not by calling {@link CassetteChatModel} directly) to prove the extension itself,
 * not just the model logic underneath it, behaves correctly.
 */
class CassetteExtensionEndToEndTest {

    private static final Path CASSETTE_FILE =
        Path.of("src", "test", "resources", "cassettes", "TogglableCallCount", "variableCalls.json");

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(CASSETTE_FILE);
    }

    @Test
    void recordsThenReplaysThroughTheRealExtension() throws IOException {
        Files.deleteIfExists(CASSETTE_FILE);

        TogglableCallCount.callsToMake = 2;
        EngineTestKit.engine("junit-jupiter")
            .selectors(selectClass(TogglableCallCount.class))
            .execute()
            .testEvents()
            .assertStatistics(stats -> stats.succeeded(1).failed(0));

        // Same class/method -> same cassette file -> now in REPLAY mode.
        EngineTestKit.engine("junit-jupiter")
            .selectors(selectClass(TogglableCallCount.class))
            .execute()
            .testEvents()
            .assertStatistics(stats -> stats.succeeded(1).failed(0));
    }

    @Test
    void unusedInteractionsFailCleanlyThroughTheRealExtension() throws IOException {
        Files.deleteIfExists(CASSETTE_FILE);

        TogglableCallCount.callsToMake = 2;
        EngineTestKit.engine("junit-jupiter")
            .selectors(selectClass(TogglableCallCount.class))
            .execute()
            .testEvents()
            .assertStatistics(stats -> stats.succeeded(1));

        // Replay, but the test body now calls the model only once: 1 of 2 recorded
        // interactions goes unused, which CassetteExtension.afterEach must catch.
        TogglableCallCount.callsToMake = 1;
        EngineTestKit.engine("junit-jupiter")
            .selectors(selectClass(TogglableCallCount.class))
            .execute()
            .testEvents()
            .assertStatistics(stats -> stats.failed(1));
    }

    /** Deliberately calls the model a variable number of times, controlled by a static field,
     *  so two separate engine runs against the SAME cassette file can simulate "the code under
     *  test now calls the model fewer times than before." */
    static class TogglableCallCount {
        static volatile int callsToMake = 2;

        @RegisterExtension
        CassetteExtension cassette = new CassetteExtension(new FakeChatModel());

        @Test
        void variableCalls() {
            ChatModel model = cassette.model();
            for (int i = 0; i < callsToMake; i++) {
                model.chat("call " + i);
            }
        }
    }
}
