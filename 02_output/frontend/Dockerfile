# Frontend image: built with Node, served as static files by nginx.
FROM node:24.13.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
RUN npx vite build

FROM nginx:1.30.5-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html

# Non-root (SB-11): nginx listens on an unprivileged port and writes only below /tmp.
USER nginx
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=6 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
