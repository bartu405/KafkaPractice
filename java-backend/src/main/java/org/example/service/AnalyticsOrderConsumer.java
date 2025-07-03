package org.example.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.example.dto.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.util.*;

import org.example.dao.AnalyticsOrderDao;
import org.jdbi.v3.core.Jdbi;


// What it does	How it works
// Listens for order events	Kafka consumer in its own thread
// Parses each event as OrderCreatedEvent	Uses Jackson for JSON parsing
// Counts orders per product	Updates and prints a map
// Runs forever in the background	Implements Runnable, started with a Thread
public class AnalyticsOrderConsumer implements Runnable {
    private final KafkaConsumer<String, String> consumer;
    private final Map<String, Integer> productOrderCounts = new HashMap<>();
    private final AnalyticsOrderDao analyticsOrderDao;

    public AnalyticsOrderConsumer(Jdbi jdbi) {
        Properties props = new Properties();
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "analytics-consumer-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("order-events"));

        // Set up JDBI DAO
        this.analyticsOrderDao = new AnalyticsOrderDao(jdbi);
    }

    @Override
    public void run() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    OrderCreatedEvent event = mapper.readValue(record.value(), OrderCreatedEvent.class);

                    //////////////////////////// Optional for counting in code, resets after docker down
                    List<Map<String, Object>> analytics = analyticsOrderDao.getAllProductCounts();
                    System.out.println("----- Analytics from DB (JDBI) -----");
                    for (Map<String, Object> row : analytics) {
                        System.out.println(row.get("product_name")
                                + ": orders=" + row.get("order_count")
                                + ", quantity=" + row.get("quantity_sum")
                                + ", revenue=" + row.get("revenue_sum"));
                    }
                    ////////////////////////////

                    // Persist analytics to MySQL using JDBI DAO
                    analyticsOrderDao.incrementProductCount(event.productName(), event.quantity(), event.price());


                } catch (Exception e) {
                    System.out.println("!!! Analytics consumer error: " + e.getMessage());
                }
            }
        }
    }
}
