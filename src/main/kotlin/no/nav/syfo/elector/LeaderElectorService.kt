package no.nav.syfo.elector

import io.kubernetes.client.extended.leaderelection.LeaderElector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.nav.syfo.service.UpdateService
import org.slf4j.LoggerFactory

class LeaderElectorService(
    private val updateService: UpdateService,
    private val leaderElector: LeaderElector = LeaderElectionUtil.createLeaderElector(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(LeaderElectorService::class.java)
    private val scope = CoroutineScope(dispatcher)

    fun start() {
        logger.info("Starting LeaderElectorService")
        scope.launch {
            leaderElector.run(
                {
                    logger.info("Is leader, running")
                    scope.launch { updateService.start() }
                },
                {
                    logger.info("Is not leader, stopping")
                    updateService.stop()
                },
            )
        }
    }
    fun stop() = scope.cancel()
}
