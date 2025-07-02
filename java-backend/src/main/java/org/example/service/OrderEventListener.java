// src/main/java/org/example/service/OrderEventListener.java
package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.example.dto.OrderCreatedEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

// By implementing Runnable, you can pass your class to a Thread, so that your code runs in the background, separate from the main program flow.
public class OrderEventListener implements Runnable {
    private final KafkaConsumer<String, String> consumer;

    private int orderCount = 0;

    public OrderEventListener() {
        Properties props = new Properties();
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "order-listener-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("order-events"));
    }

    @Override
    public void run() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        while (true) {

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {

                try {
                    OrderCreatedEvent event = mapper.readValue(record.value(), OrderCreatedEvent.class);
                    System.out.println("\uD83D\uDCEA Sending email confirmation to " + event.userEmail()
                            + " for " + event.username()
                            + " for buying " + event.productName());
                } catch (Exception e) {
                    System.out.println("❌ Error parsing or processing order event: " + e.getMessage());
                }

            }
        }
    }

    public void close() {
        consumer.close();
    }
}

//What Your Listener Code Does Now
//With the OrderEventListener code you added:
//
//It connects to your Kafka cluster (probably at localhost:9092 if you’re running Docker Compose).
//
//Subscribes to the topic called "order-events"—the same topic you’re producing to when you create an order.
//
//Starts a loop that, every second, polls for any new messages (events) sent to that topic.
//
//For each new message, it prints to the console:
//
//Order event received: {the message payload}
//So if your KafkaService sends JSON order info, you’ll see that JSON printed whenever a new order is created.
//
//This is a basic, but realistic, foundation of a microservice that “watches” for new orders in your system!

