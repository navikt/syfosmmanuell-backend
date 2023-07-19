package no.nav.syfo.elector

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import no.nav.syfo.service.UpdateService
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LeadershipHandlingTest : StringSpec({
    var isLeader = true
    val updateService = mockk<UpdateService>(relaxed = true)
    val leaderElector = mockk<LeaderElector> {
        coEvery { isLeader() } answers { isLeader }
    }

    "should star and stop" {
        val leadershipHandling = LeadershipHandling(updateService, leaderElector)
        leadershipHandling.start()
        leadershipHandling.stop()
    }

    "should start service when elected as leader" {
        val scope = TestScope()
        runTest {
            val leadershipHandling = LeadershipHandling(updateService, leaderElector, scope)
            leadershipHandling.start()

            scope.advanceTimeBy(11.seconds)
            coVerify(exactly = 1) { updateService.start() }

            isLeader = false
            scope.advanceTimeBy(6.seconds)
            coVerify(exactly = 1) { updateService.stop() }

            leadershipHandling.stop()
        }
    }

    afterTest { // Clean up after every test
        unmockkAll()
    }
})
