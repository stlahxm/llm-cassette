package dev.llmcassette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads and writes cassette files: a JSON array of {@link Interaction}, meant to be committed
 * to git so an intentional prompt change shows up as a reviewable diff on the cassette file too.
 */
final class CassetteStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private CassetteStore() {}

    static List<Interaction> read(Path file) {
        try {
            return MAPPER.readValue(file.toFile(), MAPPER.getTypeFactory().constructCollectionType(List.class, Interaction.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read cassette file: " + file, e);
        }
    }

    static void write(Path file, List<Interaction> interactions) {
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), interactions);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write cassette file: " + file, e);
        }
    }
}
