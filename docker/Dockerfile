# LAS2PEER BOOTSTRAP
FROM openjdk:14-jdk-alpine

RUN apk add --no-cache bash nodejs npm git python build-base curl sed apache-ant tar wget vim

# https://github.com/mhart/alpine-node/issues/48#issuecomment-370171836
RUN addgroup -g 1000 -S build && adduser -u 1000 -S build -G build
RUN mkdir -p /app && chown build:build /app
USER build

WORKDIR /app
RUN git clone https://github.com/ettore26/wait-for-command

RUN git clone https://github.com/rwth-acis/las2peer-registry-contracts.git
WORKDIR /app/las2peer-registry-contracts
RUN git checkout tags/v1.0.1
RUN npm install

COPY --chown=build:build ./registry-contracts/contracts/* /app/las2peer-registry-contracts/contracts/
COPY --chown=build:build ./registry-contracts/migrations/2_deploy_registry_contracts.js /app/las2peer-registry-contracts/migrations/3_deploy_consent_registry.js
COPY --chown=build:build ./start.sh /app/start.sh

RUN chmod +x /app/start.sh

ENTRYPOINT /app/start.sh
