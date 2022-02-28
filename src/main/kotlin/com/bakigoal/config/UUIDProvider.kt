package com.bakigoal.config

import org.springframework.stereotype.Component
import java.util.*

@Component
class UUIDProvider {
    fun generateOrderId(): UUID = UUID.randomUUID()
    fun generatePaymentId(): UUID = UUID.randomUUID()
    fun generateShipmentId(): UUID = UUID.randomUUID()
}
