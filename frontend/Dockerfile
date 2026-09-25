# syntax=docker/dockerfile:1
#
# stage 1: Node builds the static bundle (HTML/JS/CSS)
# stage 2: Nginx serves it and proxies /api to the backend
# Node isn't needed at runtime: the final image contains only Nginx and the built files.

# ---------- stage 1: build ----------
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first, for layer caching. "npm ci" installs exactly what package-lock.json says.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- stage 2: serve ----------
# The "unprivileged" Nginx image runs as a non-root user and listens on 8080 instead of 80.
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
