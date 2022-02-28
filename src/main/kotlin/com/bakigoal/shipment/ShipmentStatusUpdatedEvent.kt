package com.bakigoal.shipment

import java.util.*

data class ShipmentStatusUpdatedEvent(val shipmentId: UUID, val shipmentStatus: ShipmentStatus)