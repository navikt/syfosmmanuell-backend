package no.nav.syfo.elector

import io.kotest.core.spec.style.FunSpec
import io.kubernetes.client.extended.leaderelection.LeaderElector
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import no.nav.syfo.service.UpdateService

class LeaderElectorServiceTest : FunSpec({

    val mockUpdateService: UpdateService = mockk<UpdateService>(relaxed = true)
    val mockLeaderElector = mockk<LeaderElector>(relaxed = true)
    val leaderElectorService = LeaderElectorService(mockUpdateService, mockLeaderElector, Dispatchers.Unconfined)

    afterTest {
        leaderElectorService.stop()
    }

    test("verify start and stop get invoked upon leadership change") {
        val startRunnable = slot<Runnable>()
        val endRunnable = slot<Runnable>()

        every { mockLeaderElector.run(capture(startRunnable), capture(endRunnable)) } answers { }

        leaderElectorService.start()

        startRunnable.captured.run()
        coVerify { mockUpdateService.start() }

        endRunnable.captured.run()
        verify { mockUpdateService.stop() }
    }
})
