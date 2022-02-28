package com.bakigoal.payment

import org.axonframework.modelling.command.TargetAggregateIdentifier
import java.util.*

data class PayOrderCommand(
    @TargetAggregateIdentifier
    var paymentId: UUID? = null
)