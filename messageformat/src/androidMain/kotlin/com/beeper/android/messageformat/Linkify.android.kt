package com.beeper.android.messageformat

import android.util.Patterns
import java.util.regex.Pattern

actual val DEFAULT_WEB_URL_PATTERN: Pattern = Patterns.WEB_URL
actual val DEFAULT_EMAIL_ADDRESS_PATTERN: Pattern = Patterns.EMAIL_ADDRESS
