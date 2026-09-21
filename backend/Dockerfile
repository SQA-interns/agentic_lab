# Backend image (specification § 13).
# Multi-stage: the compiler and dev dependencies stay out of the runtime image.

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY backend/package.json backend/
COPY frontend/package.json frontend/
RUN npm ci
COPY backend/tsconfig.json backend/tsconfig.build.json backend/
COPY backend/src backend/src
RUN npm run build --workspace backend

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
COPY backend/package.json backend/
COPY frontend/package.json frontend/
# better-sqlite3 needs a toolchain when no prebuilt binary matches the platform.
RUN apk add --no-cache python3 make g++ \
 && npm ci --omit=dev --workspace backend \
 && apk del python3 make g++

FROM node:24-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY --from=build /app/backend/dist ./dist
COPY backend/package.json ./package.json
COPY config ./config

# The persistent volume holds the SQLite database and the JSON backup files.
RUN mkdir -p /data && chown -R node:node /data /app
USER node
VOLUME ["/data"]
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||3000)+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/server.js"]
