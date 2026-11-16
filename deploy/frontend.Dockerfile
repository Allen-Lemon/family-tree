# Keep the runtime distribution explicit. Recent Alpine/musl images can fail
# writing nginx.pid on legacy CentOS 7 kernels / Docker seccomp implementations.
# Override with a validated immutable image digest for a production release.
ARG NGINX_IMAGE=nginx:stable-bookworm

FROM node:22-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM ${NGINX_IMAGE}
# Debian nginx images use curl rather than relying on Alpine BusyBox wget.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
EXPOSE 80
