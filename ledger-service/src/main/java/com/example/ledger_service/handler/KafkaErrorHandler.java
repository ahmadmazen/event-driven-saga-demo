package com.example.ledger_service.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaErrorHandler implements ConsumerAwareListenerErrorHandler {

    @Override
    public Object handleError(Message<?> message, ListenerExecutionFailedException exception, Consumer<?, ?> consumer) {
        log.error("Error in Kafka listener for message: {}", message.getPayload(), exception);

        // Log the actual exception cause
        Throwable cause = exception.getCause();
        if (cause != null) {
            log.error("Root cause: {}", cause.getMessage(), cause);
        }

        // You can implement Dead Letter Queue logic here
        // For now, we'll just log and continue

        return null;
    }
}
