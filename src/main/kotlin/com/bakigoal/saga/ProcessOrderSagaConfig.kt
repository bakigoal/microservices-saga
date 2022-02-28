package com.bakigoal.saga

import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.TrackedEventMessage
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor
import org.axonframework.messaging.StreamableMessageSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration


@Configuration
class ProcessOrderSagaConfig {

    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerPooledStreamingEventProcessor(
            "ProcessOrderSagaProcessor",
            org.axonframework.config.Configuration::eventStore
        ) { _: org.axonframework.config.Configuration?, builder: PooledStreamingEventProcessor.Builder ->
            builder.initialToken { obj: StreamableMessageSource<TrackedEventMessage<*>?> -> obj.createTailToken() }
        }
    }
}