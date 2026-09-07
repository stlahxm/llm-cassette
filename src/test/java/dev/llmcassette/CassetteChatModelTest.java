package dev.llmcassette;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CassetteChatModelTest {

    private Path cassetteFile;

    @BeforeEach
    void setUp() throws IOException {
        cassetteFile = Files.createTempFile("cassette-test", ".json");
        Files.delete(cassetteFile); // must not exist yet, so the first CassetteChatModel records
        System.clearProperty("cassette.update");
        System.clearProperty("cassette.disabled");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(cassetteFile);
        System.clearProperty("cassette.update");
        System.clearProperty("cassette.disabled");
    }

    @Test
    void recordsOnFirstRunThenReplaysWithoutCallingTheRealModel() {
        FakeChatModel fake = new FakeChatModel();

        CassetteChatModel recording = CassetteChatModel.forTest(fake, cassetteFile);
        String firstAnswer = recording.chat("what is 2+2?");
        assertEquals(1, fake.callCount());
        assertTrue(Files.exists(cassetteFile), "recording should have written a cassette file");

        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        String replayedAnswer = replaying.chat("what is 2+2?");

        assertEquals(firstAnswer, replayedAnswer);
        assertEquals(1, fake.callCount(), "replay must not call the real model again");
    }

    @Test
    void multiTurnCallsAreMatchedInOrder() {
        FakeChatModel fake = new FakeChatModel();

        CassetteChatModel recording = CassetteChatModel.forTest(fake, cassetteFile);
        recording.chat("first question");
        recording.chat("second question");
        assertEquals(2, fake.callCount());

        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        assertEquals("fake-response-to: first question", replaying.chat("first question"));
        assertEquals("fake-response-to: second question", replaying.chat("second question"));
        assertEquals(2, fake.callCount(), "replay must not call the real model");
    }

    @Test
    void replayFailsWithADiffWhenThePromptChanged() {
        FakeChatModel fake = new FakeChatModel();
        CassetteChatModel.forTest(fake, cassetteFile).chat("original question");

        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
            () -> replaying.chat("a completely different question"));

        assertTrue(error.getMessage().contains("cassette.update=true"));
        assertTrue(error.isExpectedDefined() && error.isActualDefined(),
            "expected/actual must be set so IDEs can render a diff view");
    }

    @Test
    void pureWhitespaceChangesAreNotReportedAsDrift() {
        FakeChatModel fake = new FakeChatModel();
        CassetteChatModel.forTest(fake, cassetteFile).chat("question");

        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        assertDoesNotThrow(() -> replaying.chat("  question  "));
        assertEquals(1, fake.callCount(), "still must not call the real model on replay");
    }

    @Test
    void exhaustedCassetteFailsClearly() {
        FakeChatModel fake = new FakeChatModel();
        CassetteChatModel.forTest(fake, cassetteFile).chat("only question");

        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        replaying.chat("only question");

        AssertionError error = assertThrows(AssertionError.class, () -> replaying.chat("one too many"));
        assertTrue(error.getMessage().contains("Cassette exhausted"));
    }

    @Test
    void updateFlagForcesReRecordingEvenIfCassetteExists() {
        FakeChatModel fake = new FakeChatModel();
        CassetteChatModel.forTest(fake, cassetteFile).chat("v1 question");
        assertEquals(1, fake.callCount());

        System.setProperty("cassette.update", "true");
        CassetteChatModel.forTest(fake, cassetteFile).chat("v2 question, totally different");
        assertEquals(2, fake.callCount(), "update mode must call the real model again");

        System.clearProperty("cassette.update");
        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        assertDoesNotThrow(() -> replaying.chat("v2 question, totally different"));
        assertEquals(2, fake.callCount(), "the re-recorded cassette should now replay v2 cleanly");
    }

    @Test
    void disabledFlagAlwaysCallsTheRealModelAndSkipsCassetteEntirely() {
        FakeChatModel fake = new FakeChatModel();
        System.setProperty("cassette.disabled", "true");

        CassetteChatModel model = CassetteChatModel.forTest(fake, cassetteFile);
        model.chat("anything");
        model.chat("anything else, no cassette involved");

        assertEquals(2, fake.callCount());
        assertFalse(Files.exists(cassetteFile), "disabled mode must not write a cassette file");
    }

    @Test
    void temperatureChangeAloneCountsAsDrift() {
        ChatModel fake = new ChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse doChat(ChatRequest chatRequest) {
                return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("ok"))
                    .metadata(dev.langchain4j.model.chat.response.ChatResponseMetadata.builder().build())
                    .build();
            }
        };

        ChatRequest lowTemp = ChatRequest.builder()
            .messages(UserMessage.from("hello"))
            .temperature(0.0)
            .build();
        ChatRequest highTemp = ChatRequest.builder()
            .messages(UserMessage.from("hello"))
            .temperature(0.9)
            .build();

        CassetteChatModel.forTest(fake, cassetteFile).chat(lowTemp);

        CassetteChatModel replaying = CassetteChatModel.forTest(fake, cassetteFile);
        assertThrows(AssertionFailedError.class, () -> replaying.chat(highTemp));
    }
}
