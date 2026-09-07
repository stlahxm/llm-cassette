package dev.llmcassette;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the failure thrown when a replayed request doesn't match what was recorded.
 * Uses {@link AssertionFailedError}'s (expected, actual) constructor specifically because
 * JUnit5-aware IDEs (e.g. IntelliJ) render that as a clickable side-by-side diff view
 * automatically — reusing that existing mechanism instead of building a custom UI.
 */
final class PromptDiff {

    private PromptDiff() {}

    static AssertionFailedError mismatch(RequestSnapshot expected, RequestSnapshot actual, java.nio.file.Path cassetteFile) {
        String expectedText = render(expected);
        String actualText = render(actual);

        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
            "recorded (" + cassetteFile.getFileName() + ")",
            "actual (this run)",
            List.of(expectedText.split("\n", -1)),
            DiffUtils.diff(List.of(expectedText.split("\n", -1)), List.of(actualText.split("\n", -1))),
            2);

        String message = "The outgoing chat request no longer matches what's recorded in "
            + cassetteFile + ".\n"
            + "If this change is intentional, re-run with -Dcassette.update=true to re-record.\n\n"
            + String.join("\n", unified);

        return new AssertionFailedError(message, expectedText, actualText);
    }

    private static String render(RequestSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("model: ").append(snapshot.modelName()).append('\n');
        sb.append("temperature: ").append(snapshot.temperature()).append('\n');
        sb.append(snapshot.messages().stream()
            .map(m -> m.role() + ": " + m.text())
            .collect(Collectors.joining("\n")));
        return sb.toString();
    }
}
