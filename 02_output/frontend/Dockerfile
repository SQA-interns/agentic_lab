# Frontend image (docs/02_specification.md §12): build with the pinned Node.js image, serve the
# static files with the pinned nginx image as the non-root user nginx on port 8080 (SB-11).
FROM node:24.13.0-alpine AS build
WORKDIR /build
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.30.5-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /build/dist /usr/share/nginx/html
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx
USER nginx
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
