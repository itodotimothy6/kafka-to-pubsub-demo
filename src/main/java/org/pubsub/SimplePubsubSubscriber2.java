package org.pubsub;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.json.JSONObject;


public class SimplePubsubSubscriber2 {
    public static void main(String... args) throws Exception {
        String projectId = args[0];
        String subscriptionId = args[1];
        String datasetId = args[2];
        String tableId = args[3];

        subscribeAsyncExample(projectId, subscriptionId, datasetId, tableId);
    }

    public static void subscribeAsyncExample(String projectId, String subscriptionId, String datasetId, String tableId) {
        ProjectSubscriptionName subscriptionName =
                ProjectSubscriptionName.of(projectId, subscriptionId);

        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver =
                (PubsubMessage message, AckReplyConsumer consumer) -> {
                    // Handle incoming message, then ack the received message.
                    String record = message.getData().toStringUtf8();
                    writeToBigquery(projectId, datasetId, tableId, record);
                    consumer.ack();
                };

        Subscriber subscriber = null;
        try {
            subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
            // Start the subscriber.
            subscriber.startAsync().awaitRunning();
            System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
            // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
            subscriber.awaitTerminated(30, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            // Shut down the subscriber after 30s. Stop receiving messages.
            subscriber.stopAsync();
        }
    }

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
}