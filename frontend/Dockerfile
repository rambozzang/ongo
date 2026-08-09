# Build Stage
FROM node:20-alpine as build-stage
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production Stage
FROM nginx:stable-alpine as production-stage
COPY --from=build-stage /app/dist /usr/share/nginx/html
# Source maps are not served by this deployment; keep original source out of the public image.
RUN find /usr/share/nginx/html -type f -name '*.map' -delete
# Default Nginx config usually works for SPA if we handle 404s
# But we might need a custom default.conf if client-side routing is used
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
