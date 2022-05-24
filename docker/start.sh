#!/bin/bash
# note: not sh or zsh compatible
set -e
#set -o verbose # echo all commands before execution

# when migrating (deploying smart contracts, done by boot node),
# after eth client is seemingly ready, wait for this many extra seconds
# (because e.g. account unlocking takes time)
EXTRA_ETH_WAIT=${EXTRA_ETH_WAIT:-30}

# max wait for bootstrapping node to perform migration and share its config
# with trivial block time, this takes just a few seconds; with higher block
# times, this can be e.g. 15 minutes (but possibly much more, depending on
# difficulty)
# ... really, at this point we might as well wait forever (until the user
# kills us), but let's go for a solid six hours
CONFIG_ENDPOINT_WAIT=${CONFIG_ENDPOINT_WAIT:-21600}

NODE_ID_SEED=${NODE_ID_SEED:-$RANDOM}

PROPS_DIR=/app/HyE-YouTube-Proxy/etc/
ETH_PROPS=i5.las2peer.registry.data.RegistryConfiguration.properties
HYE_PROPS=i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy.properties
export JAVA_HOME=/opt/jdk17 && export PATH=$PATH:$JAVA_HOME/bin

function waitForEndpoint {
    /app/wait-for-command/wait-for-command.sh -c "nc -z ${1} ${2:-80}" --time ${3:-10} --quiet
}

function host { echo ${1%%:*}; }
function port { echo ${1#*:}; }
function truffleMigrate {
    echo Starting truffle migration ...
    echo "just to be sure the Eth client is ready, wait an extra $EXTRA_ETH_WAIT secs ..."
    echo "    (Yes, this is a potential source of problems, maybe increase.)"
    sleep $EXTRA_ETH_WAIT
    echo "wait over, proceeding."
    cd /app/HyE-YouTube-Proxy/docker/registry-contracts
    ./node_modules/.bin/truffle migrate --network docker_boot 2>&1 | tee migration-hye.log
    echo done. Setting contract addresses in config file ...
    # yeah, this isn't fun:
    cat migration-hye.log | grep -A5 "\(Deploying\|Replacing\|contract address\) \'\(ConsentRegistry\)\'" | grep '\(Deploying\|Replacing\|contract address\)' | tr -d " '>:" | sed -e '$!N;s/\n//;s/Deploying//;s/Replacing//;s/contractaddress/Address = /;s/./\l&/' >> "${PROPS_DIR}${HYE_PROPS}"
    cp migration-hye.log /app/HyE-YouTube-Proxy/node-storage/migration-hye.log
    echo done.
 }

if [ -n "$LAS2PEER_CONFIG_ENDPOINT" ]; then
    echo Attempting to autoconfigure registry blockchain parameters ...
    if waitForEndpoint $(host ${LAS2PEER_CONFIG_ENDPOINT}) $(port ${LAS2PEER_CONFIG_ENDPOINT}) $CONFIG_ENDPOINT_WAIT; then
        echo "Port is available (but that may just be the Docker daemon)."
        echo Downloading ...
        wget --quiet --tries=inf "http://${LAS2PEER_CONFIG_ENDPOINT}/${ETH_PROPS}" -O "${PROPS_DIR}${ETH_PROPS}"
        echo done.
    else
        echo Registry configuration endpoint specified but not accessible. Aborting.
        exit 1
    fi
fi

if [ -n "$LAS2PEER_ETH_HOST" ]; then
    echo Replacing Ethereum client host in config files ...
    ETH_HOST_SUB=$(host $LAS2PEER_ETH_HOST)
    sed -i "s|^endpoint.*$|endpoint = http://${LAS2PEER_ETH_HOST}|" "${PROPS_DIR}${ETH_PROPS}"
    sed -i "s/eth-bootstrap/${ETH_HOST_SUB}/" /app/HyE-YouTube-Proxy/docker/registry-contracts/truffle.js
    echo done.
fi

# Set defaults
if [[ -z "$HYE_SERVICE_AGENT_NAME" ]]; then
    HYE_SERVICE_AGENT_NAME="hyeAgent"
fi
if [[ -z "$HYE_SERVICE_AGENT_PW" ]]; then
    HYE_SERVICE_AGENT_PW="changeme"
fi

echo "service-agent-user.xml;${HYE_SERVICE_AGENT_PW}" > etc/startup/passphrases.txt

sed -i "s|hyeAgent|${HYE_SERVICE_AGENT_NAME}|" "${PROPS_DIR}${HYE_PROPS}"
sed -i "s|changeme|${HYE_SERVICE_AGENT_PW}|" "${PROPS_DIR}${HYE_PROPS}"

if [ -n "$WEBCONNECTOR_URL" ]; then
    sed -i "s|http://localhost:8081/hye-youtube/|${WEBCONNECTOR_URL}|" "${PROPS_DIR}${HYE_PROPS}"
fi

if [ -n "$FRONTEND_URLS" ]; then
    sed -i "s|frontendUrls = localhost:8081|frontendUrls = ${FRONTEND_URLS}|" "${PROPS_DIR}${HYE_PROPS}"
fi

if [ -z "$SLEEP_FOR" ]; then
    SLEEP_FOR=30
fi

if [ -s "/app/HyE-YouTube-Proxy/node-storage/migration-hye.log" ]; then
    echo Found old migration-hye.log, importing...
    cat /app/HyE-YouTube-Proxy/node-storage/migration-hye.log
    cat /app/HyE-YouTube-Proxy/node-storage/migration-hye.log | grep -A5 "\(Deploying\|Replacing\|contract address\) \'\(ConsentRegistry\)\'" | grep '\(Deploying\|Replacing\|contract address\)' | tr -d " '>:" | sed -e '$!N;s/\n//;s/Deploying//;s/Replacing//;s/contractaddress/Address = /;s/./\l&/' >> "${PROPS_DIR}${HYE_PROPS}"
    echo done.
fi

if [ -n "$LAS2PEER_BOOTSTRAP" ]; then
    echo Skipping migration, contracts should already be deployed
else
    if [ -n "$LAS2PEER_ETH_HOST" ]; then
        # echo Waiting for Ethereum client at $(host $LAS2PEER_ETH_HOST):$(port $LAS2PEER_ETH_HOST)...
        # if waitForEndpoint $(host $LAS2PEER_ETH_HOST) $(port $LAS2PEER_ETH_HOST) 300; then
            # echo Found Eth client.
        if [ -s "/app/HyE-YouTube-Proxy/node-storage/migration-hye.log" ]; then
            echo Migrated from logs.
        else
            # wait for seems broken somehow
            truffleMigrate
        fi
        # else
        #     echo Ethereum client not accessible. Aborting.
        #     exit 2
        # fi
    fi
fi

echo Serving config files at :8001 ...
echo -e "\a" # ding
cd /app/HyE-YouTube-Proxy/
pm2 start --silent http-server -- ./etc -p 8001

if [ -n "$LAS2PEER_BOOTSTRAP" ]; then
    if waitForEndpoint $(host ${LAS2PEER_BOOTSTRAP}) $(port ${LAS2PEER_BOOTSTRAP}) 600; then
        echo Las2peer bootstrap available, continuing.
    else
        echo Las2peer bootstrap specified but not accessible. Aborting.
        exit 3
    fi
fi

${JAVA_HOME}/bin/java -cp "lib/*" i5.las2peer.tools.UserAgentGenerator ${HYE_SERVICE_AGENT_PW} ${HYE_SERVICE_AGENT_NAME} fake@emial.com > etc/startup/service-agent-user.xml

# it's realistic for different nodes to use different accounts (i.e., to have
# different node operators). this function echos the N-th mnemonic if the
# variable WALLET is set to N. If not, first mnemonic is used
function selectMnemonic {
    declare -a mnemonics=("differ employ cook sport clinic wedding melody column pave stuff oak price" "memory wrist half aunt shrug elbow upper anxiety maximum valve finish stay" "alert sword real code safe divorce firm detect donate cupboard forward other" "pair stem change april else stage resource accident will divert voyage lawn" "lamp elbow happy never cake very weird mix episode either chimney episode" "cool pioneer toe kiwi decline receive stamp write boy border check retire" "obvious lady prize shrimp taste position abstract promote market wink silver proof" "tired office manage bird scheme gorilla siren food abandon mansion field caution" "resemble cattle regret priority hen six century hungry rice grape patch family" "access crazy can job volume utility dial position shaft stadium soccer seven")
    if [[ ${WALLET} =~ ^[0-9]+$ && ${WALLET} -lt ${#mnemonics[@]} ]]; then
    # get N-th mnemonic
        echo "${mnemonics[${WALLET}]}"
    else
        # note: zsh and others use 1-based indexing. this requires bash
        echo "${mnemonics[0]}"
    fi
}

#prepare pastry properties
echo external_address = $(curl -s https://ipinfo.io/ip):${LAS2PEER_PORT} > etc/pastry.properties

echo Starting las2peer node ...
if [ -n "$LAS2PEER_ETH_HOST" ]; then
    echo ... using ethereum boot procedure:
    ${JAVA_HOME}/bin/java $([ -n "$ADDITIONAL_JAVA_ARGS" ] && echo $ADDITIONAL_JAVA_ARGS) -cp "lib/*" --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED i5.las2peer.tools.L2pNodeLauncher \
        --service-directory service \
        --port $LAS2PEER_PORT \
        $([ -n "$LAS2PEER_BOOTSTRAP" ] && echo "--bootstrap $LAS2PEER_BOOTSTRAP") \
        --node-id-seed $NODE_ID_SEED \
        --ethereum-mnemonic "$(selectMnemonic)" \
        uploadStartupDirectory \
        $(echo $ADDITIONAL_LAUNCHER_ARGS) \
	startService\(\"i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy@0.2.0\"\) \
        startWebConnector \
        "node=getNodeAsEthereumNode()" "registry=node.getRegistryClient()" "n=getNodeAsEthereumNode()" "r=n.getRegistryClient()" \
        $(echo $ADDITIONAL_PROMPT_CMDS) \
        interactive
else
    echo ... using non-ethereum boot procedure:
    ${JAVA_HOME}/bin/java $(echo $ADDITIONAL_JAVA_ARGS) \
        -cp "lib/*" --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED i5.las2peer.tools.L2pNodeLauncher \
        --service-directory service \
        --port $LAS2PEER_PORT \
        $([ -n "$LAS2PEER_BOOTSTRAP" ] && echo "--bootstrap $LAS2PEER_BOOTSTRAP") \
        --node-id-seed $NODE_ID_SEED \
        uploadStartupDirectory \
        $(echo $ADDITIONAL_LAUNCHER_ARGS) \
        startService\(\"i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy@0.2.0\"\) \
        startWebConnector \
        $(echo $ADDITIONAL_PROMPT_CMDS) \
        interactive
fi
