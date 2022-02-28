package com.bakigoal.saga

import com.bakigoal.config.UUIDProvider
import com.bakigoal.order.CompleteOrderProcessCommand
import com.bakigoal.order.OrderConfirmedEvent
import com.bakigoal.payment.OrderPaidEvent
import com.bakigoal.payment.OrderPaymentCancelledEvent
import com.bakigoal.payment.PayOrderCommand
import com.bakigoal.saga.ProcessOrderSaga.Companion.ORDER_COMPLETE_DEADLINE
import com.bakigoal.shipment.CancelShipmentCommand
import com.bakigoal.shipment.ShipOrderCommand
import com.bakigoal.shipment.ShipmentStatus
import com.bakigoal.shipment.ShipmentStatusUpdatedEvent
import org.axonframework.test.saga.SagaTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*


internal class ProcessOrderSagaTest {

    private val testFixture = SagaTestFixture(ProcessOrderSaga::class.java)
    private var uuidProviderMock = mock(UUIDProvider::class.java)

    private val orderId = UUID.randomUUID()
    private val paymentId = UUID.randomUUID()
    private val shipmentId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        testFixture.registerResource(uuidProviderMock)
        `when`(uuidProviderMock.generateOrderId()).thenReturn(orderId)
        `when`(uuidProviderMock.generatePaymentId()).thenReturn(paymentId)
        `when`(uuidProviderMock.generateShipmentId()).thenReturn(shipmentId)
    }


    @Test
    fun onOrderConfirmedTest() {
        testFixture.givenNoPriorActivity()
            .whenPublishingA(OrderConfirmedEvent(orderId))
            .expectDispatchedCommands(PayOrderCommand(paymentId), ShipOrderCommand(shipmentId))
            .expectScheduledDeadlineWithName(Duration.of(5, ChronoUnit.DAYS), ORDER_COMPLETE_DEADLINE)
            .expectActiveSagas(1)
    }

    @Test
    fun onOrderPaidAndNotDeliveredTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .whenPublishingA(OrderPaidEvent(paymentId))
            .expectActiveSagas(1)
    }

    @Test
    fun onOrderPaidAndDeliveredTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .andThenAPublished(ShipmentStatusUpdatedEvent(shipmentId, ShipmentStatus.DELIVERED))
            .whenPublishingA(OrderPaidEvent(paymentId))
            .expectDispatchedCommands(CompleteOrderProcessCommand(orderId, isPaid = true, isDelivered = true))
            .expectNoScheduledDeadlineWithName(Duration.of(5, ChronoUnit.DAYS), ORDER_COMPLETE_DEADLINE)
            .expectActiveSagas(0)
    }

    @Test
    fun onOrderPaymentCancelledTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .whenPublishingA(OrderPaymentCancelledEvent(paymentId))
            .expectDispatchedCommands(
                CancelShipmentCommand(shipmentId),
                CompleteOrderProcessCommand(orderId, isPaid = false, isDelivered = false)
            )
            .expectNoScheduledDeadlineWithName(Duration.of(5, ChronoUnit.DAYS), ORDER_COMPLETE_DEADLINE)
            .expectActiveSagas(0)
    }

    @Test
    fun onShipmentDeliveredAndPaidTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .andThenAPublished(OrderPaidEvent(paymentId))
            .andThenAPublished(ShipmentStatusUpdatedEvent(shipmentId, ShipmentStatus.SHIPPED))
            .whenPublishingA(ShipmentStatusUpdatedEvent(shipmentId, ShipmentStatus.DELIVERED))
            .expectDispatchedCommands(CompleteOrderProcessCommand(orderId, isPaid = true, isDelivered = true))
            .expectNoScheduledDeadlineWithName(Duration.of(5, ChronoUnit.DAYS), ORDER_COMPLETE_DEADLINE)
            .expectActiveSagas(0)
    }

    @Test
    fun onShipmentDeliveredAndNotPaidTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .andThenAPublished(ShipmentStatusUpdatedEvent(shipmentId, ShipmentStatus.SHIPPED))
            .whenPublishingA(ShipmentStatusUpdatedEvent(shipmentId, ShipmentStatus.DELIVERED))
            .expectActiveSagas(1)
    }

    @Test
    fun onShipmentInTransitTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .andThenAPublished(OrderPaidEvent(paymentId))
            .whenPublishingA(ShipmentStatusUpdatedEvent(shipmentId, ShipmentStatus.SHIPPED))
            .expectActiveSagas(1)
    }

    @Test
    fun onOrderCompleteDeadlineTest() {
        testFixture.givenAPublished(OrderConfirmedEvent(orderId))
            .whenTimeElapses(Duration.of(5, ChronoUnit.DAYS))
            .expectNoScheduledDeadlines()
            .expectDispatchedCommands(CompleteOrderProcessCommand(orderId, isPaid = false, isDelivered = false))
            .expectActiveSagas(0)
    }
}