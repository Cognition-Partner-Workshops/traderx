FROM eclipse-temurin:21-jdk AS build
WORKDIR /account-service
COPY . .
RUN --mount=type=secret,id=gradle_init,target=/root/.gradle/init.d/mirror.gradle,required=false ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /account-service
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=10001:10001 /account-service/app.jar ./app.jar
EXPOSE 18088
USER 10001
ENTRYPOINT ["java","-jar","/account-service/app.jar"]