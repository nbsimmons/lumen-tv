package app.lumen.tv

import app.lumen.tv.data.ratings.ImdbRatings
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class StartupWork(
    private val scope: CoroutineScope,
    private val seerr: SeerrRepository,
    private val imdb: ImdbRatings,
    private val updates: UpdateRepository,
) {
    private val started = AtomicBoolean(false)

    fun afterHomeLoaded() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { seerr.warmRequests() }
        scope.launch { imdb.warmUp() }
        updates.checkInBackground()
    }
}
