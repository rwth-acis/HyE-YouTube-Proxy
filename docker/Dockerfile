# NODE_MODULES AND IVY BUILD CACHE
FROM openjdk:17-jdk-alpine AS buildcache
RUN apk add --update --no-cache curl py-pip python3
RUN apk add --no-cache --update  bash nodejs npm git build-base htop sed apache-ant tar wget vim && npm i -g pm2 http-server
# https://github.com/mhart/alpine-node/issues/48#issuecomment-370171836
RUN addgroup -g 1000 -S build && adduser -u 1000 -S build -G build
RUN mkdir /app-cache && chown build:build /app-cache
USER build
WORKDIR /app-cache
RUN git clone https://github.com/rwth-acis/las2peer-registry-contracts.git
WORKDIR /app-cache/las2peer-registry-contracts
RUN git checkout tags/v1.0.1
RUN npm install
WORKDIR /app-cache
# RUN git clone https://github.com/rwth-acis/las2peer/ -b develop
RUN git clone https://github.com/rwth-acis/las2peer/
WORKDIR /app-cache/las2peer
RUN git checkout tags/v1.2.1
# RUN chmod +x gradlew && ./gradlew build --exclude-task test

# LAS2PEER BOOTSTRAP
FROM mcr.microsoft.com/playwright/java
USER root
EXPOSE 9000 8001 8080
ARG LAS2PEER_PORT=9000
ENV LAS2PEER_PORT="${LAS2PEER_PORT}"
ARG LAS2PEER_BOOTSTRAP=""
ENV LAS2PEER_BOOTSTRAP="${LAS2PEER_BOOTSTRAP}"
RUN apt update && apt upgrade -y

# Install jdk 17
RUN apt remove openjdk-11-jre -y
RUN apt install libc6-i386 libc6-x32 wget curl tar -y
RUN curl -O https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz
RUN tar -xvf jdk-17_linux-x64_bin.tar.gz
RUN mv jdk-17.0.2 /opt/jdk17
RUN export JAVA_HOME=/opt/jdk17
RUN export PATH=$PATH:$JAVA_HOME/bin

RUN apt install -y python3 python3-pip
RUN apt install -y git htop npm sed vim gradle && npm i -g pm2 http-server

RUN useradd -ms /bin/bash build
RUN mkdir -p /app && chown build:build /app
USER build
WORKDIR /app
RUN git clone https://github.com/ettore26/wait-for-command

# use build cache from develop branch
# COPY --from=buildcache --chown=build:build /home/build/.ivy2 /home/build/.ivy2

# cache bust, see https://stackoverflow.com/a/39278224
ADD https://api.github.com/repos/rwth-acis/las2peer-registry-contracts/git/refs/heads/master version.json
RUN git clone https://github.com/rwth-acis/las2peer-registry-contracts.git
WORKDIR /app/las2peer-registry-contracts
RUN git checkout tags/v1.0.1
COPY --from=buildcache --chown=build:build /app-cache/las2peer-registry-contracts/node_modules /app/las2peer-registry-contracts/node_modules
# copy consent registry contract
COPY --chown=build:build ./registry-contracts/contracts/* /app/las2peer-registry-contracts/contracts/
COPY --chown=build:build ./registry-contracts/migrations/2_deploy_registry_contracts.js /app/las2peer-registry-contracts/migrations/3_deploy_consent_registry.js
RUN npm install

WORKDIR /app
# cache bust, see https://stackoverflow.com/a/39278224
ADD https://api.github.com/repos/rwth-acis/las2peer/git/refs/heads/master version.json
RUN git clone https://github.com/rwth-acis/las2peer.git
# COPY --from=buildcache --chown=build:build /app-cache/las2peer/webconnector/frontend/node_modules /app/las2peer/webconnector/frontend/node_modules

WORKDIR /app/las2peer
RUN git checkout tags/v1.2.1
RUN mkdir -p /app/las2peer/restmapper/tmp && mkdir -p /app/las2peer/webconnector/tmp && mkdir -p /app/las2peer/log && mkdir -p /app/las2peer/node-storage
RUN chmod +x gradlew && export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8" && export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME && ./gradlew build --exclude-task test --exclude-task javadoc

WORKDIR /app
COPY --chown=build:build ./etc /app/las2peer/etc
COPY --chown=build:build ./service /app/las2peer/service
COPY --chown=build:build ./keystore /app/keystore
COPY --chown=build:build ./start.sh /app/start.sh
RUN chmod +x /app/start.sh

RUN echo "Building Contact service..."
RUN git clone https://github.com/rwth-acis/las2peer-contact-service.git
WORKDIR /app/las2peer-contact-service
RUN export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME/bin && ./gradlew --version && ./gradlew jar
RUN cp contact_service/export/jars/* /app/las2peer/service

RUN echo "Building File service..."
WORKDIR /app
RUN git clone https://github.com/rwth-acis/las2peer-FileService.git
WORKDIR /app/las2peer-FileService
RUN git checkout gradle-7.2
RUN export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME/bin && ./gradlew --version && ./gradlew jar
RUN cp file_service/build/libs/* /app/las2peer/service

RUN echo "Building Userinformation service..."
WORKDIR /app
RUN git clone https://github.com/rwth-acis/las2peer-user-information-service.git
WORKDIR /app/las2peer-user-information-service
RUN git checkout java17
RUN chmod +x gradlew
RUN export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME/bin && ./gradlew --version && ./gradlew jar
RUN cp user-info-service/export/jars/* /app/las2peer/service

RUN echo "Building HyE - YouTubeProxy service..."
WORKDIR /app
RUN git clone https://github.com/rwth-acis/HyE-YouTube-Proxy.git
WORKDIR /app/HyE-YouTube-Proxy
RUN export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME/bin && export GRADLE_OPTS="-Dfile.encoding=utf-8" && ./gradlew --version && ./gradlew jar
RUN cp youtube_proxy/export/jars/* /app/las2peer/service

ENTRYPOINT /app/start.sh
