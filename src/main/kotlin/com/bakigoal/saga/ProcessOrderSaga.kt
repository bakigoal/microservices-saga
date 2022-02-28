package com.bakigoal.saga

import com.bakigoal.config.UUIDProvider
import com.bakigoal.order.CompleteOrderProcessCommand
import com.bakigoal.order.OrderConfirmedEvent
import com.bakigoal.payment.OrderPaidEvent
import com.bakigoal.payment.OrderPaymentCancelledEvent
import com.bakigoal.payment.PayOrderCommand
import com.bakigoal.shipment.CancelShipmentCommand
import com.bakigoal.shipment.ShipOrderCommand
import com.bakigoal.shipment.ShipmentStatus
import com.bakigoal.shipment.ShipmentStatusUpdatedEvent
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.deadline.DeadlineManager
import org.axonframework.deadline.annotation.DeadlineHandler
import org.axonframework.modelling.saga.EndSaga
import org.axonframework.modelling.saga.SagaEventHandler
import org.axonframework.modelling.saga.SagaLifecycle
import org.axonframework.modelling.saga.StartSaga
import org.axonframework.spring.stereotype.Saga
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

@Saga
class ProcessOrderSaga {

    companion object{
        val logger: Logger = LoggerFactory.getLogger(ProcessOrderSaga::class.java)

        const val PAYMENT_ID_ASSOCIATION = "paymentId"
        const val SHIPMENT_ID_ASSOCIATION = "shipmentId"
        const val ORDER_ID_ASSOCIATION = "orderId"
        const val ORDER_COMPLETE_DEADLINE = "OrderCompleteDeadline"
    }

    private var orderId: UUID? = null
    private var shipmentId: UUID? = null
    private var orderDeadlineId: String? = null
    private var orderIsPaid = false
    private var orderIsDelivered = false

    @Autowired
    @Transient
    private lateinit var commandGateway: CommandGateway

    @StartSaga
    @SagaEventHandler(associationProperty = ORDER_ID_ASSOCIATION)
    fun on(event: OrderConfirmedEvent, deadlineManager: DeadlineManager, uuidProvider: UUIDProvider) {
        orderId = event.orderId

        //Send a command to paid to get the order paid. Associate this Saga with the payment Id used.
        val paymentId: UUID = uuidProvider.generatePaymentId()
        SagaLifecycle.associateWith(PAYMENT_ID_ASSOCIATION, paymentId.toString())
        val payOrderCommand = PayOrderCommand(paymentId)
        logger.info("--- 1) Sending command $payOrderCommand")
        commandGateway.send<Any>(payOrderCommand)

        //Send a command to logistics to ship the order. Associate this Saga with the shipment Id used.
        val shipmentId: UUID = uuidProvider.generateShipmentId()
        SagaLifecycle.associateWith(SHIPMENT_ID_ASSOCIATION, shipmentId.toString())
        val shipOrderCommand = ShipOrderCommand(shipmentId)
        logger.info("--- 2) Sending command $shipOrderCommand")
        commandGateway.send<Any>(shipOrderCommand)
        this.shipmentId = shipmentId

        //This order should be completed in 5 days
        orderDeadlineId = deadlineManager.schedule(Duration.of(5, ChronoUnit.DAYS), ORDER_COMPLETE_DEADLINE)
    }

    @SagaEventHandler(associationProperty = PAYMENT_ID_ASSOCIATION)
    fun on(event: OrderPaidEvent, deadlineManager: DeadlineManager) {
        orderIsPaid = true
        if (orderIsDelivered) {
            completeOrderProcess(deadlineManager)
        }
    }

    @SagaEventHandler(associationProperty = PAYMENT_ID_ASSOCIATION)
    fun on(event: OrderPaymentCancelledEvent, deadlineManager: DeadlineManager) {
        // Cancel the shipment and update the Order
        commandGateway.send<Any>(CancelShipmentCommand(shipmentId))
        completeOrderProcess(deadlineManager)
    }

    @SagaEventHandler(associationProperty = SHIPMENT_ID_ASSOCIATION)
    fun on(event: ShipmentStatusUpdatedEvent, deadlineManager: DeadlineManager) {
        orderIsDelivered = ShipmentStatus.DELIVERED == event.shipmentStatus
        if (orderIsPaid && orderIsDelivered) {
            completeOrderProcess(deadlineManager)
        }
    }

    @DeadlineHandler(deadlineName = ORDER_COMPLETE_DEADLINE)
    @EndSaga
    fun on() {
        commandGateway.send<Any>(CompleteOrderProcessCommand(orderId, orderIsPaid, orderIsDelivered))
    }

    private fun completeOrderProcess(deadlineManager: DeadlineManager) {
        commandGateway.send<Any>(CompleteOrderProcessCommand(orderId, orderIsPaid, orderIsDelivered))
        deadlineManager.cancelSchedule(ORDER_COMPLETE_DEADLINE, orderDeadlineId)
        SagaLifecycle.end()
    }

}