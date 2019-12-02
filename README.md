[![Build Status][github-image]][github-url] [![Docker Pulls](https://img.shields.io/docker/pulls/cdiscount/kafka-lag-stats.svg)](https://hub.docker.com/r/cdiscount/kafka-lag-stats) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Cdiscount_kafka-lag-stats&metric=coverage)](https://sonarcloud.io/dashboard?id=Cdiscount_kafka-lag-stats)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Cdiscount_kafka-lag-stats&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=Cdiscount_kafka-lag-stats) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Cdiscount_kafka-lag-stats&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=Cdiscount_kafka-lag-stats) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Cdiscount_kafka-lag-stats&metric=security_rating)](https://sonarcloud.io/dashboard?id=Cdiscount_kafka-lag-stats)


# kafka-lag-stats

This application is a tool that monitors the lag of the consumers and gives estimations of the time remaining for a given 
message to be consumed by a given consumer.

The consumption of the message is defined by the consumer group, the topic and the partition on which the message is written.
If the partitioner used by the producer is the [DefaultPartitioner](https://github.com/apache/kafka/blob/2.3.1/clients/src/main/java/org/apache/kafka/clients/producer/internals/DefaultPartitioner.java) 
and the producer uses a partition key, then the kafka-lag-stats endpoints can be used by providing the partition key used for the message. Otherwise, the partition must be provided explicitly.

Example for topic `my-topic`, consumer group `my-group`, message key `my-key`:
```shell script
curl "http://localhost:8080/api/kafka-lag/time-remaining?group=my-group&topic=my-topic&key=my-key&publishTimestamp=2019-11-28T10:02:57.574Z"
```
```json
{
  "partition" : 0,
  "timeRemaining" : 440.32,
  "messageLag" : {
    "consumerOffset" : 2500,
    "producerOffset" : 8004,
    "lagMessages" : 5504,
    "timestamp" : "2019-11-28T10:02:57.574Z"
  },
  "speedStats" : {
    "meanSpeed" : {
      "mean" : 12.5,
      "stddev" : 12.5,
      "stddevPercent" : 100.0
    },
```

## Start server from docker image

A docker image [cbornet/kafka_lag_start](https://hub.docker.com/r/cbornet/kafka_lag_stats) can be used to quickly have a server up and running.
There is a `docker-compose.yml` at the root of the project that can be used to launch this image together with a Kafka and Zookeeper cluster.
To use it, enter:

    docker-compose up -d

The kafka-lag-stats server will be serving requests at http://localhost:8080 .

## Development

To start the application in the dev profile, run:

    ./mvnw

## Building for production

### Packaging as jar

To build the final jar and optimize the application for production, run:

    ./mvnw -Pprod clean verify

To ensure everything worked, run:

    java -jar target/*.jar

Refer to [Using JHipster in production][] for more details.

### Packaging as war

To package the application as a war in order to deploy it to an application server, run:

    ./mvnw -Pprod,war clean verify

## Testing

To launch the application's tests, run:

    ./mvnw verify

For more information, refer to the [Running tests page][].

### Code quality

Sonar is used to analyse code quality. You can start a local Sonar server (accessible on http://localhost:9001) with:

```
docker-compose -f src/main/docker/sonar.yml up -d
```

You can run a Sonar analysis with using the [sonar-scanner](https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner) or by using the maven plugin.

Then, run a Sonar analysis:

```
./mvnw -Pprod clean verify sonar:sonar
```

If you need to re-run the Sonar phase, please be sure to specify at least the `initialize` phase since Sonar properties are loaded from the sonar-project.properties file.

```
./mvnw initialize sonar:sonar
```

For more information, refer to the [Code quality page][].

## Using Docker to simplify development

A number of docker-compose configuration are available in the [src/main/docker](src/main/docker) folder to launch required third party services including a Kafka broker.

You can also fully dockerize your application and all the services that it depends on.
To achieve this, first build a docker image of your app by running:

    ./mvnw -Pprod verify jib:dockerBuild

Then run:

    docker-compose -f src/main/docker/app.yml up -d

For more information refer to [Using Docker and Docker-Compose][], this page also contains information on the docker-compose sub-generator (`jhipster docker-compose`), which is able to generate docker configurations for one or several JHipster applications.

## Credits

This application was generated using JHipster 6.5.0, you can find documentation and help at [https://www.jhipster.tech/documentation-archive/v6.5.0](https://www.jhipster.tech/documentation-archive/v6.5.0).
For further instructions on how to develop with JHipster, have a look at [Using JHipster in development][].


[jhipster homepage and latest documentation]: https://www.jhipster.tech
[jhipster 6.5.0 archive]: https://www.jhipster.tech/documentation-archive/v6.5.0
[using jhipster in development]: https://www.jhipster.tech/documentation-archive/v6.5.0/development/
[using docker and docker-compose]: https://www.jhipster.tech/documentation-archive/v6.5.0/docker-compose
[using jhipster in production]: https://www.jhipster.tech/documentation-archive/v6.5.0/production/
[running tests page]: https://www.jhipster.tech/documentation-archive/v6.5.0/running-tests/
[code quality page]: https://www.jhipster.tech/documentation-archive/v6.5.0/code-quality/
[github-image]: https://github.com/cbornet/kafka-lag-stats/workflows/Application%20CI/badge.svg
[github-url]: https://github.com/cbornet/kafka-lag-stats/actions
