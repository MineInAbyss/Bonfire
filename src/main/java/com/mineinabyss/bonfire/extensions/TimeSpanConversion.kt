package com.mineinabyss.bonfire.extensions

import com.mineinabyss.idofront.time.TimeSpan
import java.time.Duration

// TODO: Might want to move this function over to TimeSpan in Idofront
fun TimeSpan.javaDuration(): Duration = Duration.ofMillis(inMillis)