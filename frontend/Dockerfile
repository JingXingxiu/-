FROM node:22-bookworm-slim AS build

WORKDIR /workspace

# Keep dependency installation independent from application source changes.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack enable \
    && corepack prepare "$(node --print 'require("./package.json").packageManager')" --activate \
    && pnpm install --frozen-lockfile

COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
ENV VITE_API_BASE_URL=/backend
RUN pnpm build:h5

FROM nginx:1.28-alpine AS runtime

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/build/h5 /usr/share/nginx/html

EXPOSE 80
