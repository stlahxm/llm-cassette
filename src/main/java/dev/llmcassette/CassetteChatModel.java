package dev.llmcassette;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChatModel} decorator that records real calls to a cassette file the first time,
 * and replays them (without calling the real model, or needing an API key) every time after
 * that — failing loudly the moment the outgoing request stops matching what was recorded.
 *
 * <p>{@code ChatModel} funnels every convenience overload ({@code chat(String)}, etc.) through
 * the single {@link #doChat(ChatRequest)} hook, so overriding just that is enough to intercept
 * every call shape. Streaming/async ({@code doChatAsync}) is a separate hook, not covered by v1.
 */
public final class CassetteChatModel implements ChatModel {

    private enum Mode { RECORD, REPLAY, DISABLED }

    private final ChatModel delegate;
    private final Path cassetteFile;
    private final Mode mode;
    private final List<Interaction> replayCassette;
    private final List<Interaction> recorded = new ArrayList<>();
    private int replayIndex = 0;

    private CassetteChatModel(ChatModel delegate, Path cassetteFile, Mode mode, List<Interaction> replayCassette) {
        this.delegate = delegate;
        this.cassetteFile = cassetteFile;
        this.mode = mode;
        this.replayCassette = replayCassette;
    }

    /**
     * @param delegate     the real {@link ChatModel} to call when recording (or when disabled)
     * @param cassetteFile where interactions are stored; existence alone decides record vs. replay
     */
    public static CassetteChatModel forTest(ChatModel delegate, Path cassetteFile) {
        if (Boolean.getBoolean("cassette.disabled")) {
            return new CassetteChatModel(delegate, cassetteFile, Mode.DISABLED, List.of());
        }
        if (Boolean.getBoolean("cassette.update") || !Files.exists(cassetteFile)) {
            return new CassetteChatModel(delegate, cassetteFile, Mode.RECORD, List.of());
        }
        return new CassetteChatModel(delegate, cassetteFile, Mode.REPLAY, CassetteStore.read(cassetteFile));
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return switch (mode) {
            case DISABLED -> delegate.doChat(chatRequest);
            case RECORD -> record(chatRequest);
            case REPLAY -> replay(chatRequest);
        };
    }

    private ChatResponse record(ChatRequest chatRequest) {
        ChatResponse response = delegate.doChat(chatRequest);
        recorded.add(new Interaction(RequestSnapshot.from(chatRequest), response.aiMessage().text()));
        // Written after every call, not just at the end, so a test that fails partway through
        // still leaves a usable (if partial) cassette instead of losing the whole recording.
        CassetteStore.write(cassetteFile, recorded);
        return response;
    }

    private ChatResponse replay(ChatRequest chatRequest) {
        if (replayIndex >= replayCassette.size()) {
            throw new AssertionError(
                "Cassette exhausted: this test made more chat() calls than were recorded ("
                    + replayCassette.size() + ") in " + cassetteFile + ". If the code under test is now"
                    + " supposed to call the model more times, re-record with -Dcassette.update=true.");
        }
        Interaction expected = replayCassette.get(replayIndex++);
        RequestSnapshot actual = RequestSnapshot.from(chatRequest);
        if (!expected.request().normalized().equals(actual.normalized())) {
            throw PromptDiff.mismatch(expected.request(), actual, cassetteFile);
        }
        return ChatResponse.builder()
            .aiMessage(AiMessage.from(expected.response()))
            .metadata(ChatResponseMetadata.builder().build())
            .build();
    }

    /** True if every recorded interaction was actually replayed. Checked by {@link CassetteExtension} after each test. */
    boolean isFullyConsumed() {
        return mode != Mode.REPLAY || replayIndex == replayCassette.size();
    }

    int recordedCount() {
        return mode == Mode.REPLAY ? replayCassette.size() : recorded.size();
    }

    int consumedCount() {
        return replayIndex;
    }

    Path cassetteFile() {
        return cassetteFile;
    }
}
