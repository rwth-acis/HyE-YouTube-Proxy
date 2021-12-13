pragma solidity ^0.5.0;

contract ConsentRegistry {
    struct Consent {
        bytes32 consentHash;
        uint256 timestamp;
        bool revoked;
    }

    mapping(bytes32 => Consent) hashToConsent;

    // Checks whether the given hash was ever stored to the blockchain and was not yet revoked
    function hashExists(bytes32 consentHash) public view returns(bool){
        Consent storage consentObj = hashToConsent[consentHash];
        return (consentObj.timestamp != 0 && !consentObj.revoked);
    }

    // If no consent has been stored before, consent is stored
    function storeConsent(bytes32 consentHash) public {
        _createConsent(Consent(consentHash, now, true));
    }

    // Stores consent Object in mapping
    function _createConsent(Consent memory consent) private {
        hashToConsent[consent.consentHash] = consent;
    }

    // Sets the revoked attribute of the given hash to false
    function revokeConsent(bytes32 consentHash) public {
        if (!hashExists(consentHash)) revert("No consent stored for this user.");
        _createConsent(Consent(consentHash, now, false));
    }
}
