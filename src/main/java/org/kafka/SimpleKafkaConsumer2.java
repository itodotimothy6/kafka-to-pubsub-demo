package org.kafka;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONObject;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SimpleKafkaConsumer2 {
    public static void writeToBigquery(String projectId, String datasetId, String tableId, String record) {
        // Initialize the BigQuery client
        BigQuery bigquery = BigQueryOptions.newBuilder().setProjectId(projectId).build().getService();

        // Prepare data to insert
        JSONObject recordObj = new JSONObject(record);
        Map<String, Object> data = new HashMap<>();
        data.putAll(recordObj.toMap());

        // Create a row to insert
        InsertAllRequest.RowToInsert rowToInsert = InsertAllRequest.RowToInsert.of(data);

        // Insert the row into BigQuery
        TableId table = TableId.of(projectId, datasetId, tableId);
        InsertAllResponse response = bigquery.insertAll(InsertAllRequest.newBuilder(table).addRow(rowToInsert).build());

        // Check the response for success/failure
        if (response.hasErrors()) {
            System.out.println("Message insert failed");
        } else {
            System.out.println("Message inserted to BigQuery successfully.");
        }
    }

    public static void main(String[] args) {
        String topic;
        String projectId;
        String datasetId;
        String tableId;
        if (args.length == 4) {
            topic = args[0];
            projectId = args[1];
            datasetId = args[2];
            tableId = args[3];
        } else {
            throw new IllegalArgumentException("Invalid argument(s)");
        }

        String bootstrapServer = "localhost:9092";
        String groupId = "my-group";
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);


        Consumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(Collections.singleton(topic));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000)); // Poll for messages
            records.forEach(record -> {
                writeToBigquery(projectId, datasetId, tableId, record.value());
            });

            try {
                Thread.sleep(10000); // Sleep for 10 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
