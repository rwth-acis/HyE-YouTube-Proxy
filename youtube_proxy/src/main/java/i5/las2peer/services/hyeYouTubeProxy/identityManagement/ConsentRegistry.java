package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.0.1.
 */
public class ConsentRegistry extends Contract {
    private static final String BINARY = "608060405234801561001057600080fd5b506102ba806100206000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c806307a0315d14610046578063b535b5c314610065578063d1211e4c14610096575b600080fd5b6100636004803603602081101561005c57600080fd5b50356100b3565b005b6100826004803603602081101561007b57600080fd5b503561011c565b604080519115158252519081900360200190f35b610063600480360360208110156100ac57600080fd5b5035610162565b6100bc8161011c565b6100f75760405162461bcd60e51b815260040180806020018281038252602981526020018061025d6029913960400191505060405180910390fd5b600090815260016020819052604090912060028101805460ff19168317905542910155565b6000818152600160208190526040822090810154158015906101485750600281015460ff161515600114155b1561015757600091505061015d565b60019150505b919050565b61016b8161011c565b1561019657600081815260016020819052604090912060028101805460ff19169055429101556101bc565b6101bc6040518060600160405280838152602001428152602001600115158152506101bf565b50565b6000805460018101825590805281517f290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e56360039092029182015560208201517f290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e5648201556040909101517f290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e565909101805460ff191691151591909117905556fe50726f766964656420636f6e73656e742068617368206e6f7420666f756e64206f6e20636861696e2ea265627a7a7231582021ade4e662065cc4cc3ffd987ce3f3ac50725eb815e9649212ac92407560992b64736f6c63430005100032";

    public static final String FUNC_HASHEXISTS = "hashExists";

    public static final String FUNC_REVOKECONSENT = "revokeConsent";

    public static final String FUNC_STORECONSENT = "storeConsent";

    @Deprecated
    protected ConsentRegistry(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected ConsentRegistry(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected ConsentRegistry(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected ConsentRegistry(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteCall<Boolean> hashExists(byte[] consentHash) {
        final Function function = new Function(FUNC_HASHEXISTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(consentHash)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteCall<TransactionReceipt> revokeConsent(byte[] consentHash) {
        final Function function = new Function(
                FUNC_REVOKECONSENT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(consentHash)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> storeConsent(byte[] consentHash) {
        final Function function = new Function(
                FUNC_STORECONSENT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(consentHash)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static ConsentRegistry load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new ConsentRegistry(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static ConsentRegistry load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new ConsentRegistry(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static ConsentRegistry load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new ConsentRegistry(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static ConsentRegistry load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new ConsentRegistry(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<ConsentRegistry> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(ConsentRegistry.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<ConsentRegistry> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(ConsentRegistry.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<ConsentRegistry> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(ConsentRegistry.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<ConsentRegistry> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(ConsentRegistry.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }
}
