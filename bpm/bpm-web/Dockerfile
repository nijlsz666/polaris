FROM node:20-alpine AS build
WORKDIR /workspace
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY index.html vite.config.js ./
COPY src ./src
ARG VITE_BPM_API_BASE=/api
ENV VITE_BPM_API_BASE=$VITE_BPM_API_BASE
RUN pnpm build

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
