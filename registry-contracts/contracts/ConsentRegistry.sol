pragma solidity ^0.5.0;

contract ConsentRegistry {
    struct Consent {
        bytes32 consentHash;
        uint256 timestamp;
        bool revoked;
    }

    mapping(bytes32 => Consent) hashToConsent;

    // Checks whether the given hash was ever stored to the blockchain and was not yet revoked
    function hashExists(bytes32 consentHash) public view returns(bool) {
        Consent storage consentObj = hashToConsent[consentHash];
        if (consentObj.timestamp == 0) {
            return false;
        } else {
            return consentObj.revoked;
        }
    }

    // Store consent on blockchain with current timestamp
    function storeConsent(bytes32 consentHash) public {
        hashToConsent[consentHash].revoked = false;
        hashToConsent[consentHash].timestamp = now;
    }

    // Sets the revoked attribute of the given hash to false and updates timestamp
    function revokeConsent(bytes32 consentHash) public {
        if (!hashExists(consentHash)) revert("Provided consent hash not found on chain.");
        hashToConsent[consentHash].revoked = true;
        hashToConsent[consentHash].timestamp = now;
    }
}
