package dev.llmcassette;

/** One recorded request/response pair. A cassette file is an ordered list of these. */
public record Interaction(RequestSnapshot request, String response) {
}
