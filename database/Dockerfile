FROM eclipse-temurin:21-jdk AS build
WORKDIR /database
COPY . .
RUN --mount=type=secret,id=gradle_init,target=/root/.gradle/init.d/mirror.gradle,required=false ./gradlew --no-daemon jar

FROM eclipse-temurin:21-jre
WORKDIR /database
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /database/_data && chown 10001:10001 /database/_data
COPY --from=build --chown=10001:10001 /database/build/libs/database.jar ./build/libs/database.jar
COPY --chown=10001:10001 run.sh initialSchema.sql ./

EXPOSE 18082
EXPOSE 18083
EXPOSE 18084

USER 10001
ENTRYPOINT ["./run.sh"]