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
function host { echo ${1%%:*}; }
function port { echo ${1#*:}; }

function waitForEndpoint {
    /app/wait-for-command/wait-for-command.sh -c "nc -z ${1} ${2:-80}" --time ${3:-10} --quiet
}

function truffleMigrate { 
    echo Starting truffle migration ...
    echo "just to be sure the Eth client is ready, wait an extra $EXTRA_ETH_WAIT secs ..."
    echo "    (Yes, this is a potential source of problems, maybe increase.)"
    sleep $EXTRA_ETH_WAIT
    echo "wait over, proceeding."
    cd /app/las2peer-registry-contracts
    ./node_modules/.bin/truffle migrate --network docker_boot 2>&1 | tee migration.log
    echo done.
 }

if [ -n "$LAS2PEER_CONFIG_ENDPOINT" ]; then
    echo Attempting to autoconfigure registry blockchain parameters ...
    if waitForEndpoint $(host ${LAS2PEER_CONFIG_ENDPOINT}) $(port ${LAS2PEER_CONFIG_ENDPOINT}) $CONFIG_ENDPOINT_WAIT; then
        echo "Port is available (but that may just be the Docker daemon)."
        echo Downloading ...
        wget --quiet --tries=inf "http://${LAS2PEER_CONFIG_ENDPOINT}/${ETH_PROPS}" -O "${ETH_PROPS}"
        echo done.
    else
        echo Registry configuration endpoint specified but not accessible. Aborting.
        exit 1
    fi
fi

if [ -n "$LAS2PEER_ETH_HOST" ]; then
    echo Replacing Ethereum client host in config files ...
    ETH_HOST_SUB=$(host $LAS2PEER_ETH_HOST)
    sed -i "s/eth-bootstrap/${ETH_HOST_SUB}/" /app/las2peer-registry-contracts/truffle.js
    echo done.
fi
if [ -n "$LAS2PEER_ETH_HOST" ]; then
    echo Waiting for Ethereum client at $(host $LAS2PEER_ETH_HOST):$(port $LAS2PEER_ETH_HOST)...
    if waitForEndpoint $(host $LAS2PEER_ETH_HOST) $(port $LAS2PEER_ETH_HOST) 300; then
        echo Found Eth client. 
        truffleMigrate
    else
        echo Ethereum client not accessible. Aborting.
        exit 2
    fi
fi
