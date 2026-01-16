package com.beeper.android.messageformat

actual val DEFAULT_WEB_URL_PATTERN = Regex(
    """\b((?:https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""",
    RegexOption.IGNORE_CASE
).toPattern()
