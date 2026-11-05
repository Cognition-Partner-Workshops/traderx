FROM eclipse-temurin:21-jdk AS build
WORKDIR /trade-service
COPY . .
RUN --mount=type=secret,id=gradle_init,target=/root/.gradle/init.d/mirror.gradle,required=false ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /trade-service
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=10001:10001 /trade-service/app.jar ./app.jar
EXPOSE 18092
USER 10001
ENTRYPOINT ["java","-jar","/trade-service/app.jar"]