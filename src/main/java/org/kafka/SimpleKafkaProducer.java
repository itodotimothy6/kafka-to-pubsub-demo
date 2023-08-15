package org.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.UUID;

public class SimpleKafkaProducer {
    public static void main(String[] args) {
        String topic;
        if (args.length == 0) {
            topic = "my-topic";
            System.out.println("No topic provided.");
        } else {
            topic = args[0];
        }

        // Set producer properties
        String bootstrapServer = "localhost:9092";
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        // Send random messages and timestamp to the topic
        while (true) {
            String randomString = UUID.randomUUID().toString();
            LocalDateTime currentTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedTime = currentTime.format(formatter);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("message", randomString);
            jsonObject.put("timestamp", formattedTime);
            String message = jsonObject.toString();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
            producer.send(record);
            //TODO: Implement log4j
            System.out.println("Message sent: " + message);
            try {
                Thread.sleep(5000); // Sleep for 10 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
