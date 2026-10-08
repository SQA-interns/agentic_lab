FROM node:24.13.0-alpine AS build
WORKDIR /src
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-fund --no-audit
COPY . .
RUN npm run build

FROM nginx:1.30.5-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /src/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
