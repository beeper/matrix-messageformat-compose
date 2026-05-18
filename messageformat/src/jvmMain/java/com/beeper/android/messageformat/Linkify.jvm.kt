package com.beeper.android.messageformat

actual val DEFAULT_WEB_URL_PATTERN = Regex(
    """\b((?:https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;$]*[-a-zA-Z0-9+&@#/%=~_|$])""",
    RegexOption.IGNORE_CASE
).toPattern()

actual val DEFAULT_EMAIL_ADDRESS_PATTERN = Regex(
    """\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""",
    RegexOption.IGNORE_CASE
).toPattern()
