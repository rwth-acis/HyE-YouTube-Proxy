
pragma solidity ^0.5.0;

contract ConsentRegistry {
    struct Consent {
        bytes32 consentHash;
        uint256 timestamp;
        bool revoked;
    }

    mapping(bytes32 => Consent) public hashToConsent;

    // Checks whether the given hash was ever stored to the blockchain and was not yet revoked
    function hashExists(bytes32 consentHash) public view returns(bool) {
        if (hashToConsent[consentHash].timestamp == 0 || hashToConsent[consentHash].revoked) {
            return false;
        } else {
            return true;
        }
    }

    // If no consent has been stored before, consent is stored
    function storeConsent(bytes32 consentHash) public {
        hashToConsent[consentHash].timestamp = now;
        hashToConsent[consentHash].revoked = false;
    }

    // Sets the revoked attribute of the given hash to false
    function revokeConsent(bytes32 consentHash) public {
        if (!hashExists(consentHash)) revert("Provided consent hash not found on chain.");
        hashToConsent[consentHash].timestamp = now;
        hashToConsent[consentHash].revoked = true;
    }
}