FROM node:24-alpine AS base

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy application code
COPY config ./config
COPY src ./src
COPY public ./public

# Setup data and backup directories
RUN mkdir -p /app/data/backups && chown -R node:node /app

USER node

ENV NODE_ENV=production \
    PORT=3000 \
    HOST=0.0.0.0 \
    DB_PATH=/app/data/database.sqlite \
    BACKUP_DIR=/app/data/backups

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/api/health || exit 1

CMD ["node", "src/server.js"]
