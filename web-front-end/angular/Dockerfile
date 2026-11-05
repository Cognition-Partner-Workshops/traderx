FROM node:22-bookworm-slim
WORKDIR /web-front-end/angular
COPY . .
RUN npm install
ENV NG_CLI_ANALYTICS=false
EXPOSE 18093
USER node
ENTRYPOINT ["npm","run","start-prod"]