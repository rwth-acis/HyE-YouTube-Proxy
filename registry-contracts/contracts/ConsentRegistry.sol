pragma solidity ^0.5.0;

contract ConsentRegistry {
    struct Consent {
        bytes32 consentHash;
        uint256 timestamp;
        bool revoked;
    }

    Consent[] consentStorage;
    mapping(bytes32 => Consent) hashToConsent;

    // Checks whether the given hash was ever stored to the blockchain and was not yet revoked
    function hashExists(bytes32 consentHash) public view returns(bool){
        Consent storage consentObj = hashToConsent[consentHash];
        if (consentObj.timestamp != 0 && consentObj.revoked != true) {
            return true;
        } else {
            return false;
        }
    }

    // If no consent has been stored before, consent is stored
    function storeConsent(bytes32 consentHash) public {
        if (hashExists(consentHash)) {
            hashToConsent[consentHash].revoked = false;
            hashToConsent[consentHash].timestamp = now;
        } else {
            _createConsent(Consent(consentHash, now, true));
        }
    }

    // Stores consent Object in mapping
    function _createConsent(Consent memory consent) private {
        consentStorage.push(consent);
    }

    // Sets the revoked attribute of the given hash to false and updates timestamp
    function revokeConsent(bytes32 consentHash) public {
        if (!hashExists(consentHash)) revert("Provided consent hash not found on chain.");
        hashToConsent[consentHash].revoked = true;
        hashToConsent[consentHash].timestamp = now;
    }
}
