FROM eclipse-temurin:25-jdk-noble

WORKDIR /trade-service
COPY . .

EXPOSE 18092

RUN ./gradlew build
ENTRYPOINT ./gradlew bootRun