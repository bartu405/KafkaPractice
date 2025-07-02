const { Kafka } = require('kafkajs');

const kafka = new Kafka({
    clientId: 'node-order-consumer',
    brokers: ['kafka:9092'] // Or 'localhost:9092' if running locally
});

const consumer = kafka.consumer({ groupId: 'nodejs-consumer-group' });

const run = async () => {
    await consumer.connect();
    await consumer.subscribe({ topic: 'order-events', fromBeginning: true });

    await consumer.run({
        eachMessage: async ({ topic, partition, message }) => {
            const orderEvent = message.value.toString();
            try {
                const event = JSON.parse(orderEvent);
                console.log(`🟢 [Node] Order received:`, event);
                // Example: Print similar to Java's "email confirmation"
                if (event.userEmail && event.username && event.productName) {
                    console.log(`Node.js: Sending email confirmation to ${event.userEmail} for ${event.username} for buying ${event.productName}`);
                }
            } catch (err) {
                console.error('❌ Error parsing order event:', err);
            }
        }
    });
};

run().catch(console.error);
