package dev.llmcassette;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;

/**
 * JUnit5 glue: derives a cassette file path from the test class + method name and wraps
 * your real {@link ChatModel} in a {@link CassetteChatModel} for the duration of one test.
 *
 * <pre>{@code
 * @RegisterExtension
 * CassetteExtension cassette = new CassetteExtension(realChatModel);
 *
 * @Test
 * void summarizesCorrectly() {
 *     ChatModel model = cassette.model();
 *     ...
 * }
 * }</pre>
 */
public final class CassetteExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Path CASSETTE_ROOT = Path.of("src", "test", "resources", "cassettes");

    private final ChatModel realModel;
    private CassetteChatModel current;

    public CassetteExtension(ChatModel realModel) {
        this.realModel = realModel;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Path file = CASSETTE_ROOT
            .resolve(context.getRequiredTestClass().getSimpleName())
            .resolve(context.getRequiredTestMethod().getName() + ".json");
        this.current = CassetteChatModel.forTest(realModel, file);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Skip this check if the test itself already failed — one clear failure beats two confusing ones.
        if (context.getExecutionException().isPresent()) {
            return;
        }
        if (!current.isFullyConsumed()) {
            throw new AssertionError(
                "Cassette " + current.cassetteFile() + " has unused interactions: only "
                    + current.consumedCount() + " of " + current.recordedCount() + " recorded chat() calls"
                    + " were made. If the code under test now calls the model fewer times on purpose,"
                    + " re-record with -Dcassette.update=true.");
        }
    }

    /** The wrapped model to hand to the code under test. Only valid inside a test method. */
    public ChatModel model() {
        return current;
    }
}
