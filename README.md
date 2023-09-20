# Kafka to Pubsub Migration Guide

### Overview

This document is a step-by-step guide to demonstrate migration applications from
Apache Kafka to Google Cloud Pubsub using the
[Phased Migration](https://cloud.google.com/pubsub/docs/migrating-from-kafka-to-pubsub#phased_migration_using_the_pub_sub_kafka_connector)
approach.

You can learn more about the differences between Kafka and Pubsub and the Phased
Migration approach
[here](https://cloud.google.com/pubsub/docs/migrating-from-kafka-to-pubsub).

### Objective

In this demo, you will:

-   Setup a self managed Kafka cluster on GCE.
-   Deploy a simple Kafka application that streams random strings.
-   Setup Pub/Sub.
-   Migrate from Kafka to Pubsub using the
    [Pub/Sub Kafka Connector](https://github.com/googleapis/java-pubsub-group-kafka-connector).

### Costs

In this document, you will use the following billable products/services:

-   [Pub/Sub](https://cloud.google.com/pubsub/pricing)
-   [Compute Engine](https://cloud.google.com/compute/disks-image-pricing)

To generate a cost estimate based on your projected usage, use the
[pricing calculator](https://cloud.google.com/products/calculator).

### Requirements

-   Google Cloud Platform Project (Ensure you have BiqQuery & Pub/Sub write
    permissions).
-   gcloud CLI installed.
-   Java 8+ installed.

**Note**: You can use Cloud Shell to follow along this guide without incruing
any GCE cost.

### Setup Kafka

-   Download [Kafka](https://kafka.apache.org/downloads) and extract it.
    Recommend the
    [binary download](https://downloads.apache.org/kafka/3.5.1/kafka_2.13-3.5.1.tgz)
    to follow along.

    ```shell
    tar -xzf kafka_2.13-3.5.1.tgz
    cd kafka_2.13-3.5.1
    ```

-   Start Zookeeper

    ```shell
    bin/zookeeper-server-start.sh config/zookeeper.properties
    ```

-   Start the Kafka broker service. Open another terminal session and run:

    ```shell
    bin/kafka-server-start.sh config/server.properties
    ```

-   Create a kafka topic for kafka application. Open another terminal session
    and run:

    ```shell
    export TOPIC="my-topic"
    bin/kafka-topics.sh --create --topic $TOPIC --bootstrap-server localhost:9092
    ```

    **Note**: If you encounter an error due to socket connection timeout make
    sure to ensure that your listener in server.properties is pointing to
    localhost port 9092 (`listeners=PLAINTEXT://127.0.0.1:9092`)

-   Confirm topic was created successfully

    ```shell
    bin/kafka-topics.sh --describe --topic $TOPIC --bootstrap-server localhost:9092
    ```

    Output of the above cmd should look similar to below

    ```
    Topic: my-topic   TopicId: gf4ena9rSmyQXMTDj1bBmQ PartitionCount: 1   ReplicationFactor: 1    Configs:
      Topic: my-topic Partition: 0    Leader: 0   Replicas: 0 Isr: 0
    ```

### Create Kafka Application

In this demo, we'll create a simple java Kafka application that has 1 producer
and 2 consumers. The producer periodically sends a random string and a timestamp
to a Kafka topic.

To demonstrate [Phased Migration](https://cloud.google.com/pubsub/docs/migrating-from-kafka-to-pubsub#phased_migration_using_the_pub_sub_kafka_connector),
this application has 2 simple consumers. One consumer simply prints out messages
from the topic while the other writes the messages into BigQuery.

Open a new terminal and run the following commands. Do not running these
commands in the Kafka download directory.

-   Set constant variables

    ```shell
    export PROJECT_ID="<your project id>"
    export DATASET_ID="<dataset name>"
    export TABLE_ID="<table name>"
    export TOPIC="my-topic"
    ```

-   Download the Kafka application src

    ```shell
    git clone https://github.com/itodotimothy6/kafka-to-pubsub-demo.git
    cd kafka-to-pubsub-demo
    ```

-   Configure and authenticate gcloud

    ```shell
    gcloud config set project $PROJECT_ID
    gcloud auth application-default login
    gcloud services enable bigquery.googleapis.com
    ```

-   Create a BigQuery table to which the second consumer will output to. The
    schema definition of the table is "message:STRING, timestamp:STRING".

    ```
      bq mk --dataset --data_location US $PROJECT_ID:$DATASET_ID 
      bq mk --table $PROJECT_ID:$DATASET_ID.$TABLE_ID message:STRING,timestamp:STRING
    ```

-   Run the producer to begin sending messages to the topic

    ```shell
    mvn clean install
    mvn exec:java \
      -Dexec.mainClass="org.kafka.SimpleKafkaProducer" \
      -Dexec.args="$TOPIC"
    ```
    Output logs should look similar to

    ```
    ...
    Message sent: {"message":"283b7961-44cd-46d4-9061-5a22b8a1bdd7","timestamp":"2023-09-15 12:17:09"}
    Message sent: {"message":"e4c2110a-ebbe-4c96-88d1-56ffdc2a3e9a","timestamp":"2023-09-15 12:17:14"}
    ...
    ```

-   Run the first consumer that logs out messages in the topic to the console

    ```shell
    mvn clean install
    mvn exec:java \
      -Dexec.mainClass="org.kafka.SimpleKafkaConsumer1" \
      -Dexec.args="$TOPIC"
    ```

    Output logs should look similar to

    ```
    ...
    Received message: {"message":"72d46b42-5014-4d28-a6e3-04b65de63826","timestamp":"2023-09-15 12:32:47"}
    Received message: {"message":"631464dc-2651-4cce-826f-c9442beb3e98","timestamp":"2023-09-15 12:32:52"}
    ...
    ```

-   Run the second consumer that writes messages from the kafka topic to a
    BigQuery table

    ```shell
    mvn clean install
    mvn exec:java \
      -Dexec.mainClass="org.kafka.SimpleKafkaConsumer2" \
      -Dexec.args="$TOPIC $PROJECT_ID $DATASET_ID $TABLE_ID"
    ```

    Output logs should look similar to

    ```
    ...
    Message inserted to BigQuery successfully.
    Message inserted to BigQuery successfully.
    ...
    ```

    Confirm that messages are successfully being written to BigQuery in the
    [GCP console](https://pantheon.corp.google.com/bigquery).


### Setup Pub/Sub

-   Enable Pubsub

    ```shell
    gcloud services enable pubsub.googleapis.com
    ```

-   Create the Pubsub topic that will eventually replace the kafka topic. For
    simplicity, we can use the same name as the kafka topic

    ```shell
    export TOPIC="my-topic"
    gcloud pubsub topics create $TOPIC
    ```

### Phased Migration
Now that we have set up our Kafka application and have a Pub/Sub topic in place
for migration, we will proceed with migrating from Kafka to Pub/Sub.

In this migration demo, we'll be using the Google Cloud Pub/Sub Group's [Pub/Sub Kafka
Connector](https://github.com/googleapis/java-pubsub-group-kafka-connector)
which lets you migrate your kafka infrastructure in phases.

#### Phase 1

**Configure the Pub/Sub connector to forward all messages from Kafka topic to
Pub/Sub topic**

-   Acquire the kafka-to-pubsub connector jar by building the connector repo

    ```shell
    git clone https://github.com/googleapis/java-pubsub-group-kafka-connector
    cd java-pubsub-group-kafka-connector/
    mvn clean package -DskipTests=True
    ```
    You should see the resulting jar at
    `target/pubsub-group-kafka-connector-${VERSION}.jar` on success.

    Create a variable with the full path to the jar.

    ```shell
    export KAFKA_CONNECT_JAR="path/to/target/pubsub-group-kafka-connector-${VERSION}.jar"
    ```

-   Update your installed Kafka configurations with Kafka Connect configurations

-   Change directory to your kafka download folder from earlier

    ```shell
    cd kafka_2.13-3.5.1
    ```

-   Open `/config/connect-standalone.properties` in the Kafka download folder
    and add the filepath of the downloaded connector jar to plugin.path and
    uncomment the line if needed. Alternatively, you can run the below cmd

    ```shell
    echo "plugin.path=$KAFKA_CONNECT_JAR" >> config/connect-standalone.properties
    ```

-   Create a `CloudPubSubSinkConnector` config file with the kafka topic, pubsub
    project and pubsub topic needed for the migration. See sample
    `CloudPubSubSinkConnector` config file [here](https://github.com/googleapis/java-pubsub-group-kafka-connector/blob/main/config/cps-sink-connector.properties).

    ```
    cat <<EOF > config/cps-sink-connector.properties
    name=CPSSinkConnector
    connector.class=com.google.pubsub.kafka.sink.CloudPubSubSinkConnector
    tasks.max=10
    key.converter=org.apache.kafka.connect.storage.StringConverter
    value.converter=org.apache.kafka.connect.converters.ByteArrayConverter
    topics=$TOPIC
    cps.project=$PROJECT_ID
    cps.topic=$TOPIC
    EOF
    ```

-   Start the connector to begin forwarding messages from Kafka topic to Pub/Sub

    ```shell
    bin/connect-standalone.sh \
    config/connect-standalone.properties \
    config/cps-sink-connector.properties
    ```

    Confirm on the GCP console that messages are being forwarded to your Pub/Sub
    topic

#### Phase 2

**Update consumer applications to receive messages from Pub/Sub topic, while
your producer continues to publish messages to Kafka**

-   Update the consumer that prints out messages to the console to subscribe to
    Pub/Sub. In the sample `kafka-to-pubsub-demo` src, `SimplePubsubscriber1` is
    updated to read from the Pubsub topic.

  -   Create a Pub/Sub subscription

      ```shell
      export SUBSCRIPTION_ID="sub1"
      gcloud pubsub subscriptions create $SUBSCRIPTION_ID --topic=$TOPIC
      ```

  -   Run updated subscriber application

      ```shell
      cd kafka-to-pubsub-demo
      mvn exec:java \
        -Dexec.mainClass="org.pubsub.SimplePubsubSubscriber1" \
        -Dexec.args="$PROJECT_ID $SUBSCRIPTION_ID"
      ```

      Output logs should look similar to

      ```
      ...
      Id: 8827699929893588
      Data: {"message":"08afe1db-2ace-466d-bcf9-77ffc80a7f58","timestamp":"2023-09-15 15:57:34"}
      Id: 8827853608001203
      Data: {"message":"557960f7-5f2e-4156-84de-e270127c99de","timestamp":"2023-09-15 15:57:39"}
      ...
      ```

-   Update the consumer that writes to BigQuery to subscribe to Pub/Sub. In the
    sample `kafka-to-pubsub-demo` src, `SimplePubsubscriber1` is updated to read
    from the Pubsub topic.

  -   Create a Pub/Sub subscription

      ```shell
      export SUBSCRIPTION_ID="sub12"
      gcloud pubsub subscriptions create $SUBSCRIPTION_ID --topic=$TOPIC
      ```

  -   Run updated BigQuery subscriber application

      ```shell
      cd kafka-to-pubsub-demo
      mvn exec:java \
        -Dexec.mainClass="org.pubsub.SimplePubsubSubscriber2" \
        -Dexec.args="$PROJECT_ID $SUBSCRIPTION_ID $DATASET_ID $TABLE_ID"
      ```

      Output logs should look similar to

      ```
      ...
      Message inserted to BigQuery successfully.
      Message inserted to BigQuery successfully.
      ...
      ```


#### Phase 3

**Update your producers to publish directly to Pub/Sub**

-   Update the Kafka producer src to write to Pub/Sub instead of Kafka. In the
    sample `kafka-to-pubsub-demo` src, `SimplePubsubPublisher` is updated to
    to send messages to the Pubsub topic.

-   Stop the connector. You can stop the connector by terminating the running
    connector in the kafka-connect terminal session

-   Run the updated publisher application

    ```shell
    cd kafka-to-pubsub-demo
    mvn exec:java \
      -Dexec.mainClass="org.pubsub.SimplePubsubPublisher" \
      -Dexec.args="$PROJECT_ID $TOPIC"
    ```






