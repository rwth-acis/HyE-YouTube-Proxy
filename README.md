# HyE - YouTube Proxy
This repository contains the code for a [las2peer](https://github.com/rwth-acis/las2peer/) service called *hyeYouTubeProxy* with the main class *YouTubeProxy* running under the service path `/hye-youtube`.
Although this service is specifically designed to work with YouTube and YouTube video data, it may be used as a template to provide its functionality for a different service.
The underlying purpose of this service is to provide the functionality of uploading and (more or less) securely sharing YouTube cookies in order to obtain personalized YouTube content, specifically video recommendations and search results.
In order to do so, the following routes are implemented.

## Functionality
### Personalized YouTube content
The following three routes respond to GET requests by returning personalized YouTube content generated for a user different from the user sending the respective request, provided the requesting user is allowed to access at least one other user's cookies:

* Main Page (`/`): returns the video recommendations displayed on another user's YouTube main page.
* Video Page (`/watch?v=<VIDEO_ID>`): returns the video recommendations displayed in the sidebar to another user if they were to watch the video referenced by the given YouTube video ID.
* Search Page (`/results?search_query=<SEARCH_TERMS>`): returns the personalized search results for another user returned by YouTube based on the given search terms.

#### Parsing YouTube data
In order to retrieve this personalized data off of YouTube, the service relies on the browser automation framework [Microsoft Playwright](https://github.com/microsoft/playwright-java).
In order to make authenticated requests, session cookies are added to the requests.
Once the page is loaded, the HTML code is parsed using [jsoup](https://jsoup.org/) and the relevant video data is returned.

### YouTube Cookies
The cookies used for these requests are uploaded via a POST request to the `/cookies` endpoint and stored inside the [shared las2peer storage](https://github.com/rwth-acis/las2peer/wiki/Shared-Storage#las2peer-shared-storage) after getting encrypted with the owner's private key.
Using a GET request, the requesting user's cookies can be retrieved, and using a DELETE request, they can be deleted.

### Access control
las2peer provides the possibility of adding additional readers who may access data stored in the shared las2peer storage, which is how these cookies are shared among different users.
To do so, a list of las2peer User Agent IDs is sent as a POST request to the `/reader` endpoint, which can also be deleted again via a DELETE request.
Using a GET request, the User Agent IDs of all the users who shared their cookies *non-anonymously* with the requesting users are returned i.e., of all the users whose cookies the requesting user may specifically use.

#### Anonymous and non-anonymous requests
The HyE service differentiates between anonymous requests where the cookie used to obtain personalized YouTube data is chosen by the service itself (or an external service, such as [HyE - YouTube Recommendations](github.com/rwth-acis/hye-youtube-recommendations)) so that the requesting user is not aware of the identity of the cookie owner, and non-anonymous requests where the requesting user determines whose cookies are used.
Since YouTube recommendations constitute private information, linking these to a user (even if the real world identity might not be known) constitutes a higher privacy risk and thus, the permission for non-anonymous requests has to be granted specifically.

#### Request URI
Additionally, an individual permission has to be granted for each URI through which a request using the respective cookie is initiated (e.g., `localhost:8080/hye-youtube/watch`).
Thus, a service hosted under a different domain or a different route (such as the main page `/` or search page `/results`) is not allowed to use these cookies.
(Note that query parameters e.g., `v` or `search_query`, are not restricted.)

### Consent management
This second layer of protection is implemented through Ethereum smart contracts which store hashes of so called Consent object.
Such a Consent object constitutes a combination of the User Agent ID of a cookie owner and a user allowed to access the owner's cookies, a request URI, and whether the request has to be anonymous or not.
Before making a request with a foreign cookie, the service checks whether the appropriate hash for this request exists on the Ethereum blockchain and whether it has been revoked.
Such hashes are committed to the blockchain via POST requests to the `/consent` path containing the User Agent ID of the user who is granted this consent, the URI of the request, and whether the request may be made non-anonymously.
Using GET requests, users can see which Consent objects they granted to others.
And via DELETE requests, this consent can be revoked.
(Note that removing a reader via the DELETE `/reader` path does not affect the Consent objects.)

## Deployment
This service requires Gradle 7.2 and Java 17.
In order to build a JAR, which can then be uploaded via the las2peer web frontend, clone this repository and run `gradle build jar`.
Once uploaded and started, the route `/init` has to be called via a GET request in order to initialize a couple of vital components without which the service cannot function.

### Ethereum connection
As explained above, the service requires a connection to an Ethereum blockchain to function.
For this purpose, the **docker** directory contains a [docker compose](https://docs.docker.com/engine/reference/commandline/compose/) file which starts an Ethereum blockchain network with a single mining node and 10 pre-funded Ethereum wallets, which may be used for development purposes.
The docker container also automatically opens the respective network ports in order to enable connections from other network peers (for more detailed information regarding the Ethereum cluster, please refer to the respective [GitHub repository](https://github.com/rwth-acis/las2peer-ethereum-cluster)).
Furthermore, a Dockerfile is provided which is built and executed as part of the docker compose script with the purpose of building and deploying the Smart Contracts both required for las2peer and this service to properly function.
Thus, before starting the las2peer node/network to which this service is eventually uploaded, please run `docker-compose up` from inside the **docker** directory first and wait until all Smart Contracts have been deployed.
During the deployment of the Smart Contracts make special note of the address under which the **Consent Registry** is deployed, since this address has to be passed to the las2peer node in order for the hyeYouTubeProxy service to execute and evaluate the necessary Smart Contracts.

### Configurations (properties)
The Consent Registry address, as well as additional configuration parameters, are passed via the `./etc/i5.las2peer.services.hyeYouTubeProxy.YouTubeProxy.properties` file.
Below, a table with the configuration options and their purpose is provided.

| Name | Value | Optional | Description |
| ---- | ----- | -------- | ----------- |
| `consentRegistryAddress` | Hex address | No | The location the Consent Registry Smart Contract was deployed to on the Ethereum blockchain |
| `rootUri` | Web URI | No | The root address under which the service is deployed |
| `debug` | Boolean | Yes | If set, additional logging output is provided and cookies and headers may be loaded from local files |
| `cookieFile` | Local file path | Yes | If set, the cookies used for requests sent to YouTube will be read from the provided file instead of the las2peer storage (debug has to be set to `true` in order to use this option)|
| `headerFile` | Local file path | Yes | If set, the headers used for requests sent to YouTube will be read from the provided file instead of the las2peer storage (debug has to be set to `true` in order to use this option)|
| `frontendUrls` | Comma separated list of Web domains | Yes | The provided addresses are added as *Access-Control-Allow-Origin* headers to all responses sent by the service |

***Note: if cookieFile file is set but not headerFile or vice versa, neither one is regarded thus, always set both cookie- and headerFile for debugging***

## Development
Note that the class `i5.las2peer.services.hyeYouTubeProxy.identityManagement.ConsentRegistry` was generated automatically from the Smart Contract file written in [Solidity](https://soliditylang.org/) residing at `./docker/registry-contracts/contracts/ConsentRegistry.sol`.
If you wish to edit the Consent Registry, please edit this Solidity script and then generate the Java class from it by running

```bash
solc docker/registry-contracts/contracts/ConsentRegistry.sol --overwrite --bin --abi --optimize -o docker/registry-contracts/contracts/build/
web3j solidity generate -b docker/registry-contracts/contracts/build/ConsentRegistry.bin -a docker/registry-contracts/contracts/build/ConsentRegistry.abi -p i5.las2peer.services.hyeYouTubeProxy.identityManagement -o youtube_proxy/src/main/java/
```

You can find instructions how to install solc [here](https://www.npmjs.com/package/solc) and web3j [here](http://web3j.io/).
