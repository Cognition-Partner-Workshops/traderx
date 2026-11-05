# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.245.2/containers/java/.devcontainer/base.Dockerfile

# [Choice] Java version: 21, 25, 25-bookworm, 25-trixie
ARG VARIANT="25"
FROM mcr.microsoft.com/devcontainers/java:3-${VARIANT}

WORKDIR /database
COPY . .

EXPOSE 18082
EXPOSE 18083
EXPOSE 18084

RUN ./gradlew build
RUN chmod +x ./run.sh
ENTRYPOINT ./run.sh 