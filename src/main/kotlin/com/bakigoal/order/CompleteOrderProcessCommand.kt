package com.bakigoal.order

import org.axonframework.modelling.command.TargetAggregateIdentifier
import java.util.*

data class CompleteOrderProcessCommand (
    @TargetAggregateIdentifier
    var orderId: UUID? = null,
    var isPaid: Boolean = false,
    var isDelivered: Boolean = false
)