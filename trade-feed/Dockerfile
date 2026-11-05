FROM node:22-bookworm-slim
WORKDIR /trade-feed
COPY . .
RUN npm install --omit=dev
EXPOSE 18086
USER node
ENTRYPOINT ["node","index.js"]