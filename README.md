[![](https://www.jitpack.io/v/beeper/matrix-messageformat-compose.svg)](https://www.jitpack.io/#beeper/matrix-messageformat-compose)

# matrix-messageformat-compose

Kotlin multiplatform library to translate Matrix [formatted body](https://spec.matrix.org/unstable/client-server-api/#mroommessage-msgtypes) from HTML to `AnnotatedString` for native rendering in Jetpack Compose.

## Usage

Minimal working example:

```
val parseResult = remember(textInput) {
    MatrixHtmlParser().parse(
        input = textInput,
        style = MatrixBodyPreFormatStyle(),
        allowRoomMention = true,
    )
}
MatrixStyledFormattedText(
    parseResult = parseResult,
    inlineContent = parseResult.inlineImages.toInlineContent(
        LocalDensity.current,
        defaultHeight = 20.sp
    ) { info, modifier ->
        // Your inline image rendering logic here
        AsyncImage(
            model = info.uri,
            contentDescription = info.alt ?: info.title,
            modifier = modifier,
        )
    },
)
```

It is recommended to move `MatrixHtmlParser().parse()` from above example out of the UI thread
into some preprocessing logic, since that's the most expensive part and does not apply any
density or theme-specific styling yet.

You can customize rendering by following means:
- `MatrixBodyPreFormatStyle`: Provides some overrides for the pre-processing step, that are not related to screen density or theme yet.
- `MatrixBodyDrawStyle`: Holds callbacks to be executed in a `DrawScope` for drawing behind certain spans, e.g. for background rendering for code blocks and similar.
- `MatrixBodyStyledFormatter`: abstract class that applies styling to the preprocessed `AnnotatedString`, that has been deferred from the initial step in order to allow styling from a compose context without the overhead of the full parser running.
  You can implement this class manually, or start off of `DefaultMatrixBodyStyledFormatter` to only extend what you want to change from the provided defaults.
