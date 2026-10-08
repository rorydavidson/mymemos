# MyMemos web: the Kotlin rules compiled to JavaScript, the TypeScript UI built with Vite,
# then served by an unprivileged nginx that also proxies the Memos API, so the page and the
# server share an origin. Build from the repository root:
#
#   docker build -f web/Dockerfile -t mymemos-web .

# ---- 1. Kotlin/JS: the shared rules, network client, store and WebSession ----------------
FROM eclipse-temurin:17-jdk AS core
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY kotlin-js-store kotlin-js-store
COPY core-model core-model
COPY core-network core-network
# Only the pure rule files are compiled from core-data, but the directory is read by path.
COPY core-data/src/commonMain core-data/src/commonMain
COPY core-data/src/commonTest core-data/src/commonTest
COPY web/core web/core
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -PwebOnly :web-core:jsBrowserProductionLibraryDistribution

# ---- 2. The UI ---------------------------------------------------------------------------
FROM node:22-alpine AS app
WORKDIR /src/web/app
COPY --from=core /src/web/core/build/dist/js/productionLibrary /src/web/core/build/dist/js/productionLibrary
COPY web/app/package.json web/app/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY web/app ./
RUN npm run build

# ---- 3. Serve ----------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.31-alpine
# The image's entrypoint fills ${...} in templates from the environment at start-up.
COPY web/docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=app /src/web/app/dist /usr/share/nginx/html
ENV MEMOS_UPSTREAM=http://memos:5230 \
    PUBLIC_MEMOS_URL="" \
    EXTRA_CONNECT_SRC=""
EXPOSE 8080
USER 101
