ARG VARIANT="25"
FROM mcr.microsoft.com/vscode/devcontainers/java:3-${VARIANT}

WORKDIR /account-service
COPY . .

EXPOSE 18088

RUN ./gradlew build
ENTRYPOINT ./gradlew bootRun