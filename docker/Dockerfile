# LAS2PEER BOOTSTRAP
FROM mcr.microsoft.com/playwright/java

USER root
ARG LAS2PEER_PORT=9011
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

# Create non-root user (currently useless, but hopefully not for long)
RUN useradd -ms /bin/bash las2peer
RUN mkdir -p /app && chown las2peer:las2peer /app
RUN chown las2peer:las2peer /ms-playwright
USER las2peer
WORKDIR /app
RUN git clone https://github.com/ettore26/wait-for-command

# Truffle migration
RUN git clone https://github.com/rwth-acis/las2peer-registry-contracts.git
WORKDIR /app/las2peer-registry-contracts
RUN git checkout tags/v1.0.1
# copy consent registry contract
COPY --chown=las2peer:las2peer ./registry-contracts/contracts/* /app/las2peer-registry-contracts/contracts/
COPY --chown=las2peer:las2peer ./registry-contracts/migrations/2_deploy_registry_contracts.js /app/las2peer-registry-contracts/migrations/3_deploy_consent_registry.js
RUN npm install

RUN echo "Building HyE - YouTubeProxy service..."
WORKDIR /app
RUN git clone https://github.com/rwth-acis/HyE-YouTube-Proxy.git
WORKDIR /app/HyE-YouTube-Proxy
RUN export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME/bin && export GRADLE_OPTS="-Dfile.encoding=utf-8" && ./gradlew build --exclude-task test --exclude-task javadoc

# Copy service
RUN cp youtube_proxy/export/jars/* lib/
COPY --chown=las2peer:las2peer . /app/HyE-YouTube-Proxy/
RUN mkdir -p node-storage
RUN mkdir -p etc/startup

# Unfortunately, playwright seems to require root privileges... That should be fixed, but who's got the time
USER root
EXPOSE $LAS2PEER_PORT
ENTRYPOINT ./start.sh
