FROM node:22-bookworm-slim AS build
WORKDIR /reference-data
COPY . .
RUN npm install --legacy-peer-deps && npm run build && npm prune --omit=dev --legacy-peer-deps

FROM node:22-bookworm-slim
WORKDIR /reference-data
COPY --from=build --chown=node:node /reference-data/dist ./dist
COPY --from=build --chown=node:node /reference-data/node_modules ./node_modules
COPY --chown=node:node data ./data
EXPOSE 18085
USER node
ENTRYPOINT ["node","dist/main"]