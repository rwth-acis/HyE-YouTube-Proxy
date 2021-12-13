package i5.las2peer.services.hyeYouTubeProxy;

import i5.las2peer.api.Configurable;

public class ConsentRegistryConfiguration extends Configurable {
    private String consentRegistryAddress;

    public ConsentRegistryConfiguration() {
        setFieldValues();
    }

    public String getConsentRegistryAddress() {
        return consentRegistryAddress;
    }
}
