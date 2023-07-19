package no.nav.syfo.elector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.syfo.service.UpdateService
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class LeadershipHandling(
    private val updateService: UpdateService,
    private val leaderElector: LeaderElector,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val logger = LoggerFactory.getLogger(LeadershipHandling::class.java)
    private var isLeaderPreviously = false

    fun start() {
        logger.info("Starting LeaderElectorService")
        scope.launch {
            while (isActive) {
                try {
                    handleLeadership()
                    delay(5.seconds)
                } catch (e: Exception) {
                    logger.error("Error occurred in leadership handling loop delaying for 10 seconds", e)
                    delay(10.seconds)
                }
            }

            if (isLeaderPreviously) {
                logger.info("Not active, stopping")
                updateService.stop()
            }
        }
    }

    private suspend fun handleLeadership() {
        var isLeaderNow = leaderElector.isLeader()

        if (isLeaderNow && !isLeaderPreviously) {
            logger.info("Is leader, delay for 10 seconds")
            delay(10.seconds)
            isLeaderNow = leaderElector.isLeader()

            if (isLeaderNow) {
                logger.info("Is still leader, starting service")
                updateService.start()
            } else {
                logger.info("Is not leader after delay")
            }
        }

        if (!isLeaderNow && isLeaderPreviously) {
            logger.info("Is not leader, stopping")
            updateService.stop()
        }

        isLeaderPreviously = isLeaderNow
    }

    fun stop() {
        logger.info("Shutting down LeadershipHandling")
        scope.cancel()
    }
}
