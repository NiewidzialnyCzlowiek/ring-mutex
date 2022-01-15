FROM adoptopenjdk/openjdk11
RUN mkdir -p /opt/ring-mutex
COPY build/libs/ring-mutex-peer-1.0-SNAPSHOT.jar /opt/ring-mutex
CMD ["java", "-jar", "/opt/ring-mutex/ring-mutex-peer-1.0-SNAPSHOT.jar"]