# Builds both frontend bundles and bakes them into the nginx image so a
# production host never needs Node or pnpm installed. Build context is the
# repository root (see .dockerignore for the whitelist).
FROM node:22.18.0-alpine AS passenger-build
WORKDIR /src
RUN corepack enable
COPY passenger-h5/package.json passenger-h5/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY passenger-h5/ ./
RUN pnpm build

FROM node:22.18.0-alpine AS admin-build
WORKDIR /src
RUN corepack enable
COPY admin-web/package.json admin-web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY admin-web/ ./
RUN pnpm build

FROM nginx:1.28-alpine
RUN rm /etc/nginx/conf.d/default.conf && mkdir -p /var/www/certbot
COPY deploy/production/nginx/ride-dispatch.prod.conf /etc/nginx/conf.d/ride-dispatch.conf
COPY --from=passenger-build /src/dist /var/www/passenger-h5
COPY --from=admin-build /src/dist /var/www/admin-web
EXPOSE 80 443 8443
