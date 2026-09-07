package dev.llmcassette;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A deterministic, zero-network stand-in for a real LLM: echoes back a canned reply derived
 * from the last user message, and counts how many times it was actually called. Used to prove
 * llm-cassette's record/replay logic works without needing an API key in tests or CI.
 */
final class FakeChatModel implements ChatModel {

    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        callCount.incrementAndGet();
        String lastUserText = chatRequest.messages().get(chatRequest.messages().size() - 1) instanceof
            dev.langchain4j.data.message.UserMessage um ? um.singleText() : "?";
        return ChatResponse.builder()
            .aiMessage(AiMessage.from("fake-response-to: " + lastUserText))
            .metadata(ChatResponseMetadata.builder().build())
            .build();
    }

    int callCount() {
        return callCount.get();
    }
}
