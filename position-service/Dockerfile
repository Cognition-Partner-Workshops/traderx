FROM eclipse-temurin:25-jdk

WORKDIR /position-service
COPY . .

EXPOSE 18090

RUN ./gradlew build
ENTRYPOINT ./gradlew bootRun