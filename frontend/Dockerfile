FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:stable-alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.d/05-configure-dns-resolver.sh /docker-entrypoint.d/05-configure-dns-resolver.sh
RUN chmod +x /docker-entrypoint.d/05-configure-dns-resolver.sh

ENV PORT=8080
ENV BACKEND_URL=http://backend:8080
EXPOSE 8080
