package com.beeper.android.messageformat

import android.util.Patterns
import java.util.regex.Pattern

actual val DEFAULT_WEB_URL_PATTERN: Pattern = Pattern.compile(
    // Prevent the web url regex to trigger mid-word, by checking if we're at the string beginning or after some whitespace
    "(?:(?<=\\s)|^)${Patterns.WEB_URL.pattern()}",
    Patterns.WEB_URL.flags(),
)
