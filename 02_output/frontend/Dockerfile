FROM node:24.13.0-alpine AS build
WORKDIR /build
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
RUN npm run build

FROM nginx:1.30.5-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /build/dist /usr/share/nginx/html
# Non-root (SB-11): nginx listens on 8080 and keeps its runtime files in /tmp.
RUN chown -R nginx:nginx /var/cache/nginx /usr/share/nginx/html
USER nginx
EXPOSE 8080
