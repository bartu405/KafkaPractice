package org.example.service;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

public class KafkaService {
    private final KafkaProducer<String, String> producer;

    public KafkaService() {
        Properties props = new Properties();
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        this.producer = new KafkaProducer<>(props);
    }

    public void sendOrderCreatedEvent(String orderJson) {
        ProducerRecord<String, String> record = new ProducerRecord<>("order-events", orderJson);
        producer.send(record, new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
                if (exception != null) {
                    System.err.println("Failed to send Kafka message: " + exception.getMessage());
                } else {
                    System.out.println("Kafka message sent to topic " + metadata.topic() +
                            " partition " + metadata.partition() +
                            " offset " + metadata.offset());
                }
            }
        });
        // Optionally: producer.flush(); // Ensures the message is sent immediately
    }

    public void close() {
        producer.close();
    }
}
