# Beware that this dockerfile is made to be run from the parent directory.
# keep the last steps in sync with usejar (if only docker-compose had functionality to build images separately from services)

FROM maven:3.5.3-jdk-8 as build

WORKDIR /core
ARG http_proxy
ARG https_proxy
ARG mavensonatypenexus

COPY ./pom.xml ./docker/configuremavenproxy ./
RUN mkdir ~/.m2 && ./configuremavenproxy >~/.m2/settings.xml \
	&& mkdir -p src/test/java/ \
	&& echo 'public class Dummy { void dumm() { } }' >> src/test/java/Dummy.java \
	&& mvn -B clean package \
	&& rm -rf target src
COPY . .
RUN mvn -B clean package \
	&& mv target/electric* /core/job.jar

FROM flink:1.10.0-scala_2.11
RUN apt-get update && apt-get -y install --no-install-recommends kafkacat && rm -rf /lib/dpkg
# Good idea to keep these two lines in sync with the matching ones in flink.Dockerfile

RUN mkdir /job && chown flink:flink /job
USER flink

WORKDIR /job/
CMD flink run -m jobmanager1:8081 job.jar

COPY --from=build /core/job.jar /job/
