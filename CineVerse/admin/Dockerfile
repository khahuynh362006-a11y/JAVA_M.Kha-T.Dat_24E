FROM node:22-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci --no-audit --no-fund
COPY frontend/ ./frontend/
COPY admin/ ./admin/
RUN cd admin && npm run build
FROM nginx:1.28-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/admin/dist /usr/share/nginx/html
EXPOSE 80
