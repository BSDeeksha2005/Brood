FROM node:24-alpine AS web-build
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS java-build
WORKDIR /app
COPY sim/ ./sim/
COPY lang/ ./lang/
COPY server/ ./server/
COPY --from=web-build /app/web/dist/ ./server/src/main/resources/static/
RUN cd server && ../sim/gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /opt/brood
COPY --from=java-build /app/server/build/install/brood-server/ ./
ENV PORT=10000
ENV BROOD_HOST=0.0.0.0
EXPOSE 10000
CMD ["/opt/brood/bin/brood-server"]
