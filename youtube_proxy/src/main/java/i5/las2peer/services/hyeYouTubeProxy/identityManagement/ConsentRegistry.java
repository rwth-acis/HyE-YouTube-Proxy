package i5.las2peer.services.hyeYouTubeProxy.identityManagement;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
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
    private static final String BINARY = "608060405234801561001057600080fd5b50610251806100206000396000f3fe608060405234801561001057600080fd5b506004361061004c5760003560e01c806307a0315d14610051578063b535b5c314610070578063d1211e4c146100a1578063e42cbab2146100be575b600080fd5b61006e6004803603602081101561006757600080fd5b50356100fb565b005b61008d6004803603602081101561008657600080fd5b5035610169565b604080519115158252519081900360200190f35b61006e600480360360208110156100b757600080fd5b50356101ae565b6100db600480360360208110156100d457600080fd5b50356101cf565b604080519384526020840192909252151582820152519081900360600190f35b61010481610169565b61013f5760405162461bcd60e51b81526004018080602001828103825260298152602001806101f46029913960400191505060405180910390fd5b6000908152602081905260409020426001808301919091556002909101805460ff19169091179055565b6000818152602081905260408120600101541580610198575060008281526020819052604090206002015460ff165b156101a5575060006101a9565b5060015b919050565b6000908152602081905260409020426001820155600201805460ff19169055565b60006020819052908152604090208054600182015460029092015490919060ff168356fe50726f766964656420636f6e73656e742068617368206e6f7420666f756e64206f6e20636861696e2ea265627a7a72315820e59508ab4e9f639280ab318a4d24b59681322bcdfc69cffb81bd221b2fe1b5ff64736f6c63430005100032";

    public static final String FUNC_HASHEXISTS = "hashExists";

    public static final String FUNC_HASHTOCONSENT = "hashToConsent";

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

    public RemoteCall<Tuple3<byte[], BigInteger, Boolean>> hashToConsent(byte[] param0) {
        final Function function = new Function(FUNC_HASHTOCONSENT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}));
        return new RemoteCall<Tuple3<byte[], BigInteger, Boolean>>(
                new Callable<Tuple3<byte[], BigInteger, Boolean>>() {
                    @Override
                    public Tuple3<byte[], BigInteger, Boolean> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple3<byte[], BigInteger, Boolean>(
                                (byte[]) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (Boolean) results.get(2).getValue());
                    }
                });
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
