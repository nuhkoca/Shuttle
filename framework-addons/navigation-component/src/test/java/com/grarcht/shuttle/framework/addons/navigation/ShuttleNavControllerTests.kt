package com.grarcht.shuttle.framework.addons.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import com.grarcht.shuttle.framework.CargoShuttle
import com.grarcht.shuttle.framework.addons.ArchtTestTaskExecutorExtension
import com.grarcht.shuttle.framework.addons.bundle.MockBundleFactory
import com.grarcht.shuttle.framework.addons.coroutines.CompositeDisposableHandle
import com.grarcht.shuttle.framework.addons.warehouse.ShuttleDataWarehouse
import com.grarcht.shuttle.framework.result.ShuttlePickupCargoResult
import com.grarcht.shuttle.framework.screen.ShuttleFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import java.io.Serializable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val ACTION_ID = 5000
private val ARGUMENTS = Bundle()

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ArchtTestTaskExecutorExtension::class)
class ShuttleNavControllerTests {
    private var compositeDisposableHandle: CompositeDisposableHandle? = null
    private var navController = mock(NavController::class.java)
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope

    @BeforeEach
    fun `run before each test`() {
        testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope()
        Dispatchers.setMain(testDispatcher)
        compositeDisposableHandle = CompositeDisposableHandle()
    }

    @AfterEach
    fun `run after each test`() {
        compositeDisposableHandle?.dispose()
        Dispatchers.resetMain()
        testDispatcher.cancel()
        testScope.cancel()
    }

    @Test
    fun verifyNavigateWithNavDirections() = testScope.runTest {
        val cargoId = "cargoId1"
        val cargo = Cargo(cargoId, 50)
        val directions = TestNavDirections()
        val shuttleFacade = mock(ShuttleFacade::class.java)
        val shuttleWarehouse = ShuttleDataWarehouse()
        val cargoShuttle = CargoShuttle(shuttleFacade, shuttleWarehouse)
        var channel: Channel<ShuttlePickupCargoResult>?
        var numberOfValidSteps = 0
        var storeId = ""
        val countDownLatch = CountDownLatch(2)

        doNothing().`when`(navController).navigate(directions)

        // Navigate to the destination
        val shuttleNavController = ShuttleNavController.navigateWith(
            shuttleWarehouse,
            shuttleFacade,
            navController,
            directions,
            bundleFactory = MockBundleFactory()
        )
        // The guts of function cleanShuttleOnReturnTo are tested in the framework module's tests.
        shuttleNavController
            .transport(cargoId, cargo)
            .deliver()

        awaitOnLatch(countDownLatch, 1, TimeUnit.SECONDS)

        // pickup the cargo
        val disposableHandle = launch(Dispatchers.Main) {
            channel = cargoShuttle.pickupCargo<Cargo>(cargoId)
            channel
                ?.consumeAsFlow()
                ?.collectLatest { shuttleResult ->
                    when (shuttleResult) {
                        is ShuttlePickupCargoResult.Loading -> {
                            numberOfValidSteps++
                        }
                        is ShuttlePickupCargoResult.Success<*> -> {
                            storeId = (shuttleResult.data as Cargo).cargoId
                            numberOfValidSteps++
                            countDownLatch.countDown()
                            cancel()
                        }
                        is ShuttlePickupCargoResult.Error<*> -> {
                            countDownLatch.countDown()
                            cancel()
                        }
                    }
                }
        }.invokeOnCompletion {
            it?.let {
                println(it.message ?: "Error when getting the serializable.")
            }
        }
        compositeDisposableHandle?.add(disposableHandle)


        delay(1000L)
        advanceUntilIdle()

        Assertions.assertEquals(cargoId, storeId)
        Assertions.assertEquals(2, numberOfValidSteps)
        cargoShuttle.cleanShuttleFromAllDeliveries()
    }

    @Test
    fun verifyNavigateWithNavId() = testScope.runTest {
        val cargoId = "cargoId2"
        val cargo = Cargo(cargoId, 150)
        val shuttleFacade = mock(ShuttleFacade::class.java)
        val shuttleWarehouse = ShuttleDataWarehouse()
        val cargoShuttle = CargoShuttle(shuttleFacade, shuttleWarehouse)
        var channel: Channel<ShuttlePickupCargoResult>?
        var numberOfValidSteps = 0
        var storeId = ""
        val countDownLatch = CountDownLatch(2)

        // Navigate to the destination
        val shuttleNavController = ShuttleNavController.navigateWith(
            shuttleWarehouse,
            shuttleFacade,
            navController,
            R.id.nav_host_fragment_container, // used since it's a res id.  For the test, it doesn't matter.
            bundleFactory = MockBundleFactory()
        )
        // The guts of function cleanShuttleOnReturnTo are tested in the framework module's tests.
        shuttleNavController
            .transport(cargoId, cargo)
            .deliver()

        awaitOnLatch(countDownLatch, 1, TimeUnit.SECONDS)

        val disposableHandle = launch(Dispatchers.Main) {
            channel = cargoShuttle.pickupCargo<Cargo>(cargoId)
            channel
                ?.consumeAsFlow()
                ?.collectLatest { shuttleResult ->
                    when (shuttleResult) {
                        is ShuttlePickupCargoResult.Loading -> {
                            numberOfValidSteps++
                        }
                        is ShuttlePickupCargoResult.Success<*> -> {
                            storeId = (shuttleResult.data as Cargo).cargoId
                            numberOfValidSteps++
                            countDownLatch.countDown()
                            cancel()
                        }
                        is ShuttlePickupCargoResult.Error<*> -> {
                            countDownLatch.countDown()
                            cancel()
                        }
                    }
                }
        }.invokeOnCompletion {
            it?.let {
                println(it.message ?: "Error when getting the serializable.")
            }
        }
        compositeDisposableHandle?.add(disposableHandle)

        awaitOnLatch(countDownLatch, 1, TimeUnit.SECONDS)

        Assertions.assertEquals(cargoId, storeId)
        Assertions.assertEquals(2, numberOfValidSteps)
    }

    @Suppress("SameParameterValue")
    private fun awaitOnLatch(countDownLatch: CountDownLatch, timeout: Long, timeUnit: TimeUnit) {
        @Suppress("BlockingMethodInNonBlockingContext", "SameParameterValue")
        countDownLatch.await(timeout, timeUnit)
    }

    private data class Cargo(val cargoId: String, val numberOfBoxes: Int) : Serializable

    private class TestNavDirections(
        override val actionId: Int = ACTION_ID,
        override val arguments: Bundle = ARGUMENTS
    ) : NavDirections
}
