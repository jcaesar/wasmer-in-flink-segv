# Beware that this dockerfile is made to be run from the parent directory.
# keep the last steps in sync with usejar (if only docker-compose had functionality to build images separately from services)

FROM rust as src
WORKDIR /src
RUN wget https://github.com/wasmerio/wasmer/archive/0.17.0.tar.gz \
	&& echo '0972d1d8c5d4e7b38bcc6866dadc55e33b61143288917053d2aaa2e5f41b7c1f  0.17.0.tar.gz' | sha256sum -c \
	&& tar xvf 0.17.0.tar.gz \
	&& mv wasmer-* wasmer \
	&& rm 0.17.0.tar.gz
RUN wget https://github.com/wasmerio/wasmer-java/archive/0.2.0.tar.gz \
	&& echo '1c359f5af6dfced9e6afac1ab062a0a1a3734a0fda5ab39fb11d50c2a1d071d5  0.2.0.tar.gz' | sha256sum -c \
	&& tar xvf 0.2.0.tar.gz \
	&& mv wasmer-java-* wasmer-java \
	&& rm 0.2.0.tar.gz
COPY wasmer.patch .
RUN patch -p1 <wasmer.patch && rm wasmer.patch

FROM maven:3.5.3-jdk-8 as jaheaders
COPY ./docker/configuregradleproxy /usr/local/bin/
ARG http_proxy
ARG https_proxy
RUN mkdir ~/.gradle/ && configuregradleproxy >~/.gradle/gradle.properties
WORKDIR /app/wasmer-java
COPY --from=src /src/wasmer-java/gradle /app/wasmer-java/gradle
COPY --from=src /src/wasmer-java/gradlew /src/wasmer-java/settings.gradle /src/wasmer-java/build.gradle /app/wasmer-java/
RUN ./gradlew generateJniHeaders
COPY --from=src /src/ /app
RUN ./gradlew --info clean generateJniHeaders

FROM rust as ruvendor
WORKDIR /app/wasmer-java
COPY --from=src /src/ /app
RUN cargo vendor

FROM rust as rubuilder
COPY --from=src /src/ /app
COPY --from=jaheaders /app/wasmer-java/include /app/wasmer-java/
COPY --from=ruvendor /app/wasmer-java/vendor /app/wasmer-java/vendor
COPY cargo-vendor-config /app/wasmer-java/.cargo/config
WORKDIR /app/wasmer-java
RUN env RUSTFLAGS='-g' cargo build
RUN cd /app/wasmer-java/target/debug/ && sha256sum libwasmer_jni.so >chks.txt && cat chks.txt && ls -lah libwasmer_jni.so

FROM maven:3.5.3-jdk-8 as jabuilder
COPY ./docker/configuregradleproxy /usr/local/bin/
COPY --from=src /src/wasmer-java/ .
COPY --from=jaheaders /root/.gradle /root/.gradle
COPY --from=rubuilder /app/wasmer-java/target/debug/libwasmer_jni.so /app/wasmer-java/target/debug/chks.txt artifacts/linux-amd64/
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
COPY --from=jabuilder /build/libs/wasmer-jni-amd64-linux-0.2.0.jar /root/.m2/repository/org/wasmer/wasmer-jni-amd64-linux/0.2.0/wasmer-jni-amd64-linux-0.2.0.jar
COPY . .
RUN mvn -B clean package \
	&& mv target/electric* /core/job.jar
COPY --from=rubuilder /app/wasmer-java/target/debug/chks.txt /core/
RUN unzip /core/job.jar org/wasmer/native/linux-amd64/libwasmer_jni.so && cd org/wasmer/native/linux-amd64/ && sha256sum -c </core/chks.txt

FROM flink:1.10.0-scala_2.11
RUN apt-get update && apt-get -y install --no-install-recommends kafkacat && rm -rf /lib/dpkg
# Good idea to keep these two lines in sync with the matching ones in flink.Dockerfile

RUN mkdir /job && chown flink:flink /job
USER flink

WORKDIR /job/
CMD flink run -m jobmanager1:8081 job.jar

COPY --from=build /core/job.jar /job/
