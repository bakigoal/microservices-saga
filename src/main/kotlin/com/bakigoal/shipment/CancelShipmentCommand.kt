package com.bakigoal.shipment

import org.axonframework.modelling.command.TargetAggregateIdentifier
import java.util.*

data class CancelShipmentCommand(
    @TargetAggregateIdentifier
    var shipmentId: UUID? = null
)