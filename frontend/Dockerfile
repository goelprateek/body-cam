FROM node:22-bookworm-slim AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/bodycam-frontend/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
