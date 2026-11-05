# [Choice] Node.js version
ARG VARIANT=5.2.5-26-bookworm
FROM mcr.microsoft.com/devcontainers/javascript-node:${VARIANT}

WORKDIR /web-front-end/angular
COPY . .
EXPOSE 18093

# [Optional] Uncomment this section to install additional OS packages.
# RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
#     && apt-get -y install --no-install-recommends <your-package-list-here>

# [Optional] Uncomment if you want to install more global node modules
# RUN su node -c "npm install -g <your-package-list-here>"
ENTRYPOINT npm run start-prod