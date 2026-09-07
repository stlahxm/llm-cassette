package dev.llmcassette;

import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.List;

/**
 * The part of a {@link ChatRequest} llm-cassette records and compares: which model/temperature
 * it targeted, and what it said. Not every {@link dev.langchain4j.model.chat.request.ChatRequestParameters}
 * field is tracked, just the two most likely to silently change behavior — see CONTRIBUTING.md
 * for extending this to more parameters.
 */
public record RequestSnapshot(String modelName, Double temperature, List<ChatMessageSnapshot> messages) {

    static RequestSnapshot from(ChatRequest request) {
        List<ChatMessageSnapshot> messages = request.messages().stream()
            .map(ChatMessages::snapshot)
            .toList();
        return new RequestSnapshot(
            request.parameters().modelName(),
            request.parameters().temperature(),
            messages);
    }

    /** Trims each message's text for comparison, so pure-whitespace edits are never reported as drift. */
    RequestSnapshot normalized() {
        return new RequestSnapshot(modelName, temperature, messages.stream().map(ChatMessageSnapshot::normalized).toList());
    }
}
