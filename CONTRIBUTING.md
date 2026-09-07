# Contributing to llm-cassette

This is a young, small project — issues and small, focused PRs are welcome.
Please keep PRs single-purpose (one feature or fix per PR, no drive-by
reformatting) and include a test where the change touches non-trivial logic.

The core record/replay/diff/unused-interaction logic is intentionally
complete for the common case (plain-text, synchronous, single-turn or
multi-turn `ChatModel` usage) — the good-first-issues below are genuine
extension points, not gaps in the basics.

## Good first issues

- **Streaming/async support (`StreamingChatModel`, `doChatAsync`).** v1 only
  intercepts the synchronous `ChatModel.doChat` hook. Streaming is a
  meaningfully different feature surface (partial tokens instead of one
  response) and deserves its own design, not a quick bolt-on.
- **Track more `ChatRequestParameters` fields.** Currently only `modelName`
  and `temperature` are recorded/compared (see `RequestSnapshot`). Fields
  like `topP`, `maxOutputTokens`, or `stopSequences` changing could also be
  meaningful drift worth catching — add them to `RequestSnapshot` and its
  `normalized()`/equality behavior.
- **Multimodal and tool-execution message support.** `ChatMessages.snapshot`
  currently throws `UnsupportedOperationException` for anything other than
  plain-text `SystemMessage`/`UserMessage`/`AiMessage`. Supporting images or
  `ToolExecutionResultMessage` needs a real design decision about what
  "drift" means for binary content, not just a text diff.
- **A Maven/Gradle plugin to bulk-update stale cassettes.** Right now
  `-Dcassette.update=true` re-records everything the test run touches. A
  plugin that lists which cassette files are stale (recorded against an old
  request shape) before you decide to re-record could be a nice DX layer
  on top of the current mechanism.

## Reporting a bug

Include: the LangChain4j version, the `ChatModel` implementation in use, and
if possible the cassette JSON file that reproduces the issue (redact any
real content you don't want to share).

## Running the test suite

```
mvn test
```

`CassetteChatModelTest` covers the core record/replay/diff/mismatch logic
directly. `CassetteExtensionEndToEndTest` runs a small nested test class
through the real JUnit5 engine (via `junit-platform-testkit`) to verify the
actual `@RegisterExtension` wiring, not just the model logic underneath it.
Both need to pass before a PR is merged.
