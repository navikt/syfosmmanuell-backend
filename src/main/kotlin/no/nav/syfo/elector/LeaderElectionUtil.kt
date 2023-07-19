package no.nav.syfo.elector

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig
import io.kubernetes.client.extended.leaderelection.LeaderElector
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.util.Config
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object LeaderElectionUtil {
    private val client: ApiClient = Config.defaultClient()
    private val hostname: String = InetAddress.getLocalHost().hostName
    private val namespace: String = "teamsykmelding"
    private val leaseDuration: Duration = 15.seconds
    private val renewDeadline: Duration = 10.seconds
    private val retryPeriod: Duration = 2.seconds
    init {
        Configuration.setDefaultApiClient(client)
    }

    fun createLeaderElector(): LeaderElector {
        val leaderElectionConfig = LeaderElectionConfig(
            LeaseLock(namespace, hostname, hostname),
            leaseDuration.toJavaDuration(),
            renewDeadline.toJavaDuration(),
            retryPeriod.toJavaDuration(),
        )
        return LeaderElector(leaderElectionConfig)
    }
}
