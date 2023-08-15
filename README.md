# Kafka to Pubsub Phased Migration Guide

## Overview

This document is a step by step guide to demonstrate migration applications from Apache Kafka to Google Cloud's Pubsub using the [Phased Migration](https://cloud.google.com/pubsub/docs/migrating-from-kafka-to-pubsub#phased_migration_using_the_pub_sub_kafka_connector) approach.

You can learn more about the differences between Kafka and Pubsub and the Phased Migration approach [here](https://cloud.google.com/pubsub/docs/migrating-from-kafka-to-pubsub).

## Objectives

In this demo, you will:
- Setup a self managed Kafka Cluster on GCE
- Deploy a simple Kafka application that streams random messages and consumes the messages
- Setup Pubsub
- Migrate from Kafka to Pubsub using the [PubSub Kafka Connector](https://github.com/googleapis/java-pubsub-group-kafka-connector)

## Costs

In this document, you will use the following billable products/services:
- [Pub/Sub](https://cloud.google.com/pubsub/pricing)
- [Compute Engine](https://cloud.google.com/compute/disks-image-pricing)

To generate a cost estimate based on your projected usage, use the [pricing calculator](https://cloud.google.com/products/calculator).

## Requirements

- Google Cloud Platform
- gcloud CLI
- Java 8+

**Note**: You can use Cloud Shell to follow along this guide without incruing any GCE cost

## Setup Kafka
- Download [kafka](https://kafka.apache.org/downloads) and extract it
    ```
    tar -xzf kafka_2.13-3.5.0.tgz
    cd kafka_2.13-3.5.0
    ```
- Start Zookeeper
    ```
    bin/zookeeper-server-start.sh config/zookeeper.properties
    ```
- Start the broker service. Open another terminal session and run:
    ```
    bin/kafka-server-start.sh config/server.properties
    ```
- Create a kafka topic for kafka application. Open another terminal session and run:
    ```
    export TOPIC="my-topic"
    bin/kafka-topics.sh --create --topic $TOPIC --bootstrap-server localhost:9092
    ```
- Confirm topic was created successfully
    ```
    bin/kafka-topics.sh --describe --topic $TOPIC --bootstrap-server localhost:9092
    ```
  
## Create Java Application
In this demo, we'll create a kafka application with 1 producer and 2 consumers.
The producers send random string and a timestamp to a kafka topic. 
To demonstrate phased migration, we'll create 2 consumers. 
One consumer simply prints out messages while the other writes the messages into BigQuery.
- Clone application src
  ```
  git clone <repo>
  cd kafka-to-pubsub-demo
  
  export PROJECT_ID="datastream-rm"
  export DATASET_ID="mydataset"
  export TABLE_ID="mytable"
  gcloud config set project $PROJECT_ID
  gcloud auth application-default login
  
  ```
- Setup BigQuery
  ```
  export PROJECT_ID="datastream-rm"
  export DATASET_ID="mydataset"
  export TABLE_ID="mytable"
  gcloud config set project $PROJECT_ID
  gcloud auth application-default login
  
  bq mk --dataset --data_location US $PROJECT_ID:$DATASET_ID
  bq mk --table $PROJECT_ID:$DATASET_ID.$TABLE_ID message:STRING,timestamp:STRING
  ```
- Run the producer and consumers in different terminal sessions
  ```
  mvn exec:java -Dexec.mainClass="org.kafka.SimpleKafkaProducer" -Dexec.args="my-topic"
  mvn exec:java -Dexec.mainClass="org.kafka.SimpleKafkaConsumer1" -Dexec.args="my-topic"
  mvn exec:java -Dexec.mainClass="org.kafka.SimpleKafkaConsumer2" -Dexec.args="my-topic datastream-rm mydataset mytable"
  ```
  
### Setup Pubsub and create topic
  ```
    gcloud services enable pubsub.googleapis.com
    gcloud projects add-iam-policy-binding datastream-rm --member="user:<user>" --role=roles/pubsub.admin
    gcloud pubsub topics create $TOPIC
  ```

### Phased Migration
In this demo we'll be using the Google Cloud Pub/Sub Group Kafka Connector to migrate from kafka to Pubsub

- Acquire the jar
```build

```



