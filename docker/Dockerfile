# LAS2PEER BOOTSTRAP
FROM openjdk:17-jdk-alpine
USER root

# Install dependencies
RUN apk add --update --no-cache curl py-pip python3
RUN apk add --no-cache bash nodejs npm git build-base htop sed apache-ant tar wget vim && npm i -g pm2 http-server

# Create non-root user (currently useless, but hopefully not for long)
RUN addgroup -g 1000 -S las2peer && adduser -u 1000 -S las2peer -G las2peer
RUN mkdir -p /app && chown las2peer:las2peer /app
USER las2peer
WORKDIR /app
RUN git clone https://github.com/ettore26/wait-for-command

WORKDIR /app
RUN echo "Building HyE-YouTube-Proxy ..."
RUN git clone https://github.com/rwth-acis/HyE-YouTube-Proxy.git
WORKDIR /app/HyE-YouTube-Proxy
RUN git checkout origin/no-playwright
RUN export GRADLE_OPTS="-Dfile.encoding=utf-8" && ./gradlew build --exclude-task test --exclude-task javadoc

# Copy service
RUN cp youtube_proxy/export/jars/* lib/
COPY --chown=las2peer:las2peer . /app/HyE-YouTube-Proxy/
RUN mkdir -p node-storage

# Build registry contract
WORKDIR /app/HyE-YouTube-Proxy/docker/registry-contracts
RUN npm install

# Unfortunately, playwright seems to require root privileges... That should be fixed, but who's got the time
#USER root
WORKDIR /app/HyE-YouTube-Proxy
RUN mv docker/start.sh ./
RUN mv docker/etc/pastry.properties ./etc
ENTRYPOINT ./start.sh
