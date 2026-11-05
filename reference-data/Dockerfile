FROM node:26.13.0-bookworm-slim

WORKDIR /reference-data
COPY package*.json ./
RUN npm ci
COPY . .
EXPOSE 18085
CMD ["npm", "run", "start"]