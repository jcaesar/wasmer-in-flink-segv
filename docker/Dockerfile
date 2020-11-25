# Beware that this dockerfile is made to be run from the parent directory.
# keep the last steps in sync with usejar (if only docker-compose had functionality to build images separately from services)

FROM rust as src
WORKDIR /src
RUN wget https://github.com/wasmerio/wasmer-java/archive/0.2.0.tar.gz \
	&& echo '1c359f5af6dfced9e6afac1ab062a0a1a3734a0fda5ab39fb11d50c2a1d071d5  0.2.0.tar.gz' | sha256sum -c \
	&& tar xvf 0.2.0.tar.gz --strip-components=1 \
	&& rm 0.2.0.tar.gz

FROM maven:3.5.3-jdk-8 as jaheaders
COPY ./docker/configuregradleproxy /usr/local/bin/
ARG http_proxy
ARG https_proxy
RUN mkdir ~/.gradle/ && configuregradleproxy >~/.gradle/gradle.properties
WORKDIR /app
COPY --from=src /src/ .
RUN ./gradlew --info generateJniHeaders

FROM rust as ruplanner
WORKDIR app
RUN cargo install cargo-chef
COPY --from=src /src/ .
RUN cargo chef prepare  --recipe-path recipe.json

FROM rust as rucacher
WORKDIR app
RUN cargo install cargo-chef
COPY --from=ruplanner /app/recipe.json recipe.json
RUN env RUSTFLAGS='-g' cargo chef cook --recipe-path recipe.json

FROM rust as rubuilder
WORKDIR app
COPY --from=src /src/ .
COPY --from=jaheaders /app/include .
COPY --from=rucacher /app/target target
RUN env RUSTFLAGS='-g' cargo build
RUN cd /app/target/debug/ && sha256sum libwasmer_jni.so >chks.txt && cat chks.txt && ls -lah libwasmer_jni.so

FROM maven:3.5.3-jdk-8 as jabuilder
COPY ./docker/configuregradleproxy /usr/local/bin/
COPY --from=src /src/ .
COPY --from=jaheaders /root/.gradle /root/.gradle
COPY --from=rubuilder /app/target/debug/libwasmer_jni.so /app/target/debug/chks.txt artifacts/linux-amd64/
RUN sed -ri 's/commandLine.*"build-rust".*$/commandLine "echo", "NOOP"/' build.gradle
RUN cd artifacts/linux-amd64/ && sha256sum -c <chks.txt
RUN ./gradlew --info build
RUN sha256sum /build/libs/wasmer-jni-amd64-linux-0.2.0.jar

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
RUN rm /root/.m2/repository/org/wasmer/wasmer-jni-amd64-linux/0.2.0/wasmer-jni-amd64-linux-0.2.0.jar
COPY --from=jabuilder /build/libs/wasmer-jni-amd64-linux-0.2.0.jar /root/.m2/repository/org/wasmer/wasmer-jni-amd64-linux/0.2.0/wasmer-jni-amd64-linux-0.2.0.jar
RUN find ~/.m2/repository/org/wasmer/ && sha256sum             /root/.m2/repository/org/wasmer/wasmer-jni-amd64-linux/0.2.0/wasmer-jni-amd64-linux-0.2.0.jar
COPY . .
RUN mvn -B clean package \
	&& mv target/electric* /core/job.jar
COPY --from=rubuilder /app/target/debug/chks.txt /core/
RUN find ~/.m2/repository/org/wasmer/ && sha256sum /root/.m2/repository/org/wasmer/wasmer-jni-amd64-linux/0.2.0/wasmer-jni-amd64-linux-0.2.0.jar
RUN unzip /core/job.jar org/wasmer/native/linux-amd64/libwasmer_jni.so && cd org/wasmer/native/linux-amd64/ && sha256sum -c </core/chks.txt

FROM flink:1.10.0-scala_2.11
RUN apt-get update && apt-get -y install --no-install-recommends kafkacat && rm -rf /lib/dpkg
# Good idea to keep these two lines in sync with the matching ones in flink.Dockerfile

RUN mkdir /job && chown flink:flink /job
USER flink

WORKDIR /job/
CMD flink run -m jobmanager1:8081 job.jar

COPY --from=build /core/job.jar /job/
