const ConsentRegistry = artifacts.require('ConsentRegistry')

module.exports = function (deployer) {
    deployer.deploy(ConsentRegistry)
};
