package dev.llmcassette;

import java.util.List;

import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * The part of a {@link ChatRequest} llm-cassette records and compares:
 * model parameters and the request messages. Not every
 * {@link dev.langchain4j.model.chat.request.ChatRequestParameters} field is
 * tracked — see CONTRIBUTING.md for extending this to more parameters.
 */
public record RequestSnapshot(String modelName,
        Double temperature,
        Double topP,
        Integer maxOutputTokens,
        List<String> stopSequences,
        List<ChatMessageSnapshot> messages) {

    static RequestSnapshot from(ChatRequest request) {
        List<ChatMessageSnapshot> messages = request.messages().stream()
                .map(ChatMessages::snapshot)
                .toList();

        return new RequestSnapshot(
                request.parameters().modelName(),
                request.parameters().temperature(),
                request.parameters().topP(),
                request.parameters().maxOutputTokens(),
                request.parameters().stopSequences(),
                messages);
    }

    /**
     * Trims each message's text for comparison, so pure-whitespace edits are never
     * reported as drift.
     */
    RequestSnapshot normalized() {
        return new RequestSnapshot(
                modelName,
                temperature,
                topP,
                maxOutputTokens,
                stopSequences,
                messages.stream()
                        .map(ChatMessageSnapshot::normalized)
                        .toList());
    }
}
