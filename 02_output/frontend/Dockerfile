# syntax=docker/dockerfile:1
# Build stage: pinned Node.js 22.23.3 (D-09).
FROM node:22.23.3@sha256:363e1587494626837fa7f9a23bdb453d13b0ff3c67c705c2805cfc69c2d2fad7 AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Runtime stage: pinned nginx 1.28.0 running as the unprivileged nginx user on port 8080 (SB-11).
FROM nginx:1.28.0@sha256:552e7481ca93ffccd046aa658dbbed22caefbc09c66fa7cd247cbb90b8a5c609
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html
RUN mkdir -p /tmp/nginx && chown -R nginx:nginx /tmp/nginx /var/cache/nginx
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=6 CMD curl -fsS http://127.0.0.1:8080/healthz || exit 1
