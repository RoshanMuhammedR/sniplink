# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM node:24-alpine AS build

WORKDIR /build

# Lockfile-only layer: dependencies reinstall only when the lockfile changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# `npm run build` is `tsc -b && vite build`, so a type error fails the image.
RUN npm run build

# ---- runtime --------------------------------------------------------------
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
	CMD wget -qO /dev/null http://localhost/ || exit 1
