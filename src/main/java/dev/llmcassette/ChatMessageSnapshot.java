package dev.llmcassette;

/**
 * A stored/compared form of one {@link dev.langchain4j.data.message.ChatMessage}: its role
 * and its text. v1 only supports plain-text system/user/AI messages (see {@link ChatMessages}) —
 * multimodal content and tool-execution messages are a separate feature surface, not handled here.
 */
public record ChatMessageSnapshot(String role, String text) {

    /** Trims the text for comparison purposes, so trailing/leading whitespace alone never counts as drift. */
    ChatMessageSnapshot normalized() {
        return new ChatMessageSnapshot(role, text == null ? "" : text.trim());
    }
}
