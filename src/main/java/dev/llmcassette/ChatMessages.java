package dev.llmcassette;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Extracts a role/text pair from the plain-text {@link ChatMessage} subtypes llm-cassette
 * supports in v1: {@link SystemMessage}, {@link UserMessage} (single text part only), and
 * {@link AiMessage}. Multipart user messages (images) and tool-execution messages are a
 * separate feature surface, deliberately out of scope here — see CONTRIBUTING.md.
 */
final class ChatMessages {

    private ChatMessages() {}

    static ChatMessageSnapshot snapshot(ChatMessage message) {
        if (message instanceof SystemMessage m) {
            return new ChatMessageSnapshot("SYSTEM", m.text());
        }
        if (message instanceof UserMessage m) {
            return new ChatMessageSnapshot("USER", m.singleText());
        }
        if (message instanceof AiMessage m) {
            return new ChatMessageSnapshot("AI", m.text());
        }
        throw new UnsupportedOperationException(
            "llm-cassette v1 only supports SystemMessage/UserMessage/AiMessage; got "
                + message.type() + ". Multimodal and tool-execution messages aren't handled yet.");
    }
}
