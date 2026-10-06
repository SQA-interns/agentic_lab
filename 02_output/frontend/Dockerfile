# Frontend image: Vite build on the pinned Node image, served by unprivileged nginx (SB-11).
FROM node:24.13.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
RUN npm run build

FROM nginx:1.30.5-alpine
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
RUN rm -f /etc/nginx/conf.d/default.conf \
    && chown -R nginx:nginx /etc/nginx/conf.d /var/cache/nginx
ENV BACKEND_UPSTREAM=backend:8080
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
