package org.nem.beta;

import org.nem.beta.utility.UtilityFunctions;
import org.nem.beta.model.WSMonitorIncomingHandler;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import org.nem.core.crypto.Hash;
import org.nem.core.crypto.KeyPair;
import org.nem.core.crypto.PublicKey;
import org.nem.core.crypto.PrivateKey;
import org.nem.core.messages.PlainMessage;
import org.nem.core.messages.SecureMessage;
import org.nem.core.model.primitive.Amount;
import org.nem.core.model.primitive.BlockHeight;
import org.nem.core.model.primitive.Quantity;
import org.nem.core.serialization.BinarySerializer;
import org.nem.core.time.SystemTimeProvider;
import org.nem.core.time.TimeInstant;
import org.nem.core.model.*;
import org.nem.core.model.mosaic.*;
import org.nem.core.model.namespace.*;

public class SimplifyNEM {

    //Global Variables
    public static UtilityFunctions utilityFunc;
    public static final String NEM_ADDRESS = "http://104.128.226.60:7890";
    public static final String WSNEM_ADDRESS = "http://104.128.226.60:7778";
    public static final String API_ANNOUNCE_TRANSACTION = "/transaction/announce";
    public static final String API_ALL_TRANSACTIONS = "/account/transfers/all";
    public static final String API_UNCONFIRMED_TRANSACTIONS = "/account/unconfirmedTransactions";
    public static final String TESTNET_NAMESPACE_SINKING = "TAMESPACEWH4MKFMBCVFERDPOOP4FK7MTDJEYP35";
    public static final String TESTNET_MOSAIC_SINKING = "TBMOSAICOD4F54EE5CDMR23CCBGOAM2XSJBR5OLC";
    public static final String TESTNET_APOSTILLE_SINKING = "TC7MCY5AGJQXZQ4BN3BOPNXUVIGDJCOHBPGUM2GE";
    public static final long MOSAIC_MAX_QUANTITY = 9_000_000_000_000_000L;

    public static void main(String[] args) {
        utilityFunc = new UtilityFunctions(NEM_ADDRESS);

        //Send Message and/or XEM
        //String results = SendTransaction(senderPrivateKey, receiverAddress, 1000, "this is a test message, it should be more than 32 bytes", null, 0);
        //System.out.println(String.format("Results: %s", results));

        //Rent new namespace
        //String results = RentNamespace(senderPrivateKey, "coffee", null);
        //System.out.println(String.format("Results: %s", results));
        //results = RentNamespace(senderPrivateKey, "columbia", "coffee");
        //System.out.println(String.format("Results: %s", results));

        //New mosaic definition
        //String results = CreateMosaic(senderPrivateKey, "coffee.columbia:caffeine", "points", "100000");
        //System.out.println(String.format("Results: %s", results));

        //Transfer mosaic
        //String results = SendTransaction(senderPrivateKey, receiverAddress, 0,"sending mosaics", "coffee.columbia:caffeine", 1);
        //System.out.println(String.format("Results: %s", results));

        //Send Encrypted Message, use file to simulate encrypted data
        //String results = SendEncryptedMessage(senderPrivateKey, receiverAddress, "LocationToFile\\sample.txt");
        //System.out.println(String.format("Results: %s", results));

        //Create MultiSig Account, MultiSig Account must have sufficient XEM to perform this operation hence senderPrivateKey is to feed the account with funds
        //String results = CreateMultisigAcc(3,2, senderPrivateKey);
        //System.out.println(String.format("Results: %s", results));

        //Start a MultiSig Transaction, get Account details from CreateMultisigAcc() outputs
        //String results = MultiSigTransaction(coSigPrivKey, multiSigPubKey, receiverAddress, 1000, null, null, 0);
        //System.out.println(String.format("Results: %s", results));

        //Cosign Transaction, get Transaction Hash from MultiSigTransaction() results
        //String results = CoSigTransaction(2ndCoSigPrivKey, multiSigAddress, transactionHash);
        //System.out.println(String.format("Results: %s", results));

        //Get existing transactions made
        //GetAllConfirmedMessages(senderAddress);

        //Listen to web socket for transactions
        //StartWebSocketService(senderAddress);

        utilityFunc.Disconnect();
    }

    private static String SendTransaction(String senderPrivateKey, String receiverAddress, long amount, String message, String mosaicId, long mosaicQty) {
        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        Account senderAccount = new Account(new KeyPair(PrivateKey.fromHexString(senderPrivateKey)));
        Account recipientAccount = new Account(Address.fromEncoded(receiverAddress));
        TransferTransactionAttachment attachment = (message == null && mosaicId == null && mosaicQty != 0) ? null : new TransferTransactionAttachment();

        if (mosaicId != null && mosaicQty != 0) {
            MosaicId mosyId = MosaicId.parse(mosaicId);
            MosaicFeeInformation mosaicFeeInformation = utilityFunc.findById(mosyId);
            Double mosaicQuantityDouble = Double.valueOf(mosaicQty) * Math.pow(10, mosaicFeeInformation.getDivisibility());
            attachment.addMosaic(mosyId, Quantity.fromValue(mosaicQuantityDouble.longValue()));
        }

        if (message != null) {
            //PlainMessage for non encrypted messages, SecureMessage for encrypted messages. Fees calculated after encryption
            PlainMessage PM = new PlainMessage(message.getBytes());
            attachment.setMessage(PM);
        }

        TransferTransaction transaction = new TransferTransaction(timeStamp, senderAccount, recipientAccount, Amount.fromNem(amount), attachment);
        //572500, 975000 Testnet Fee Fork Heights
        //875000, 1110000 Mainnet Fee Fork Heights
        transaction.setFee(new DefaultTransactionFeeCalculator(utilityFunc, () -> new BlockHeight(utilityFunc.getBlockHeight()), new BlockHeight(975000)).calculateMinimumFee(transaction));
        transaction.setDeadline(timeStamp.addHours(23));
        transaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(transaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", transaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);
    }

    private static String RentNamespace(String accountPrivateKey, String namespaceToRent, String parentNamespace) {
        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        Account namespaceAccount = new Account(new KeyPair(PrivateKey.fromHexString(accountPrivateKey)));
        Account sinkingfeeAccount = new Account(Address.fromEncoded(TESTNET_NAMESPACE_SINKING));

        ProvisionNamespaceTransaction namespaceTransaction = new ProvisionNamespaceTransaction(timeStamp, namespaceAccount, sinkingfeeAccount, (parentNamespace == null) ? Amount.fromNem(100) : Amount.fromNem(10), new NamespaceIdPart(namespaceToRent), (parentNamespace == null) ? null : new NamespaceId(parentNamespace));
        namespaceTransaction.setFee(Amount.fromMicroNem(new Double(0.15 * Amount.MICRONEMS_IN_NEM).longValue()));
        namespaceTransaction.setDeadline(timeStamp.addHours(23));
        namespaceTransaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(namespaceTransaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", namespaceTransaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);

    }

    private static String CreateMosaic(String accountPrivateKey, String mosaicName, String mosaicDescription, String mosaicQty) {
        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        Account mosaicAccount = new Account(new KeyPair(PrivateKey.fromHexString(accountPrivateKey)));

        Properties mosaicProperty = new Properties();
        mosaicProperty.put("divisibility", "0");
        mosaicProperty.put("initialSupply", mosaicQty);
        mosaicProperty.put("supplyMutable", "false");
        mosaicProperty.put("transferable", "true");
        MosaicDefinition mosaicDef = new MosaicDefinition(mosaicAccount, MosaicId.parse(mosaicName), new MosaicDescriptor(mosaicDescription), new DefaultMosaicProperties(mosaicProperty), new MosaicLevy(MosaicTransferFeeType.Absolute, mosaicAccount, MosaicId.parse("nem:xem"), Quantity.ZERO));

        MosaicDefinitionCreationTransaction mosaicTransaction = new MosaicDefinitionCreationTransaction(timeStamp, mosaicAccount, mosaicDef);
        mosaicTransaction.setFee(Amount.fromMicroNem(new Double(0.15 * Amount.MICRONEMS_IN_NEM).longValue()));
        mosaicTransaction.setDeadline(timeStamp.addHours(23));
        mosaicTransaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(mosaicTransaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", mosaicTransaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);
    }

    private static String SendEncryptedMessage(String senderPrivateKey, String receiverAddress, String fileLocation) {
        File encryptData = new File(fileLocation);
        byte[] encryptDataBytes = new byte[(int) encryptData.length()];
        try {
            FileInputStream fileStream = new FileInputStream(encryptData);
            fileStream.read(encryptDataBytes);
            fileStream.close();
        } catch (Exception e) {
            System.out.println(String.format("Exception caught: %s", e.getMessage()));
            e.printStackTrace();
        }

        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        Account senderAccount = new Account(new KeyPair(PrivateKey.fromHexString(senderPrivateKey)));
        Account receiverAccount = new Account(Address.fromEncoded(receiverAddress));
        TransferTransactionAttachment attachment = new TransferTransactionAttachment();
        SecureMessage encryptMessage = SecureMessage.fromEncodedPayload(senderAccount, receiverAccount, encryptDataBytes);
        attachment.setMessage(encryptMessage);

        TransferTransaction encryptTransaction = new TransferTransaction(timeStamp, senderAccount, receiverAccount, Amount.ZERO, attachment);
        //572500, 975000 Testnet Fee Fork Heights
        //875000, 1110000 Mainnet Fee Fork Heights
        encryptTransaction.setFee(new DefaultTransactionFeeCalculator(utilityFunc, () -> new BlockHeight(utilityFunc.getBlockHeight()), new BlockHeight(975000)).calculateMinimumFee(encryptTransaction));
        encryptTransaction.setDeadline(timeStamp.addHours(23));
        encryptTransaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(encryptTransaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", encryptTransaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);
    }

    private static String CreateMultisigAcc(int noSigAccount, int noSigRequired, String fundingPrivKey) {
        KeyPair MultiSigKeys = new KeyPair();
        Account MultiSigAccount = new Account(MultiSigKeys);

        //This is needed for MultiSigTransaction()
        System.out.println(String.format("MultiSig Private Key: %s", MultiSigKeys.getPrivateKey()));
        System.out.println(String.format("MultiSig Public Key: %s", MultiSigKeys.getPublicKey()));
        System.out.println(String.format("MultiSig Address: %s", MultiSigAccount.getAddress()));
        String result = SendTransaction(fundingPrivKey, MultiSigAccount.getAddress().toString(), 5000, null, null, 0);
        if (!result.toLowerCase().contains("success")) {
            System.out.println("Failed to populate MultiSig Account");
            return result;
        }
        List<MultisigCosignatoryModification> coSigModColl = new ArrayList<MultisigCosignatoryModification>();
        MultisigMinCosignatoriesModification minCosigMod = new MultisigMinCosignatoriesModification(noSigRequired);

        for (int i = 0; i < noSigAccount; ++i) {
            KeyPair newKeys = new KeyPair();
            Account newAccount = new Account(newKeys);
            coSigModColl.add(new MultisigCosignatoryModification(MultisigModificationType.AddCosignatory, newAccount));
            System.out.println(String.format("CoSig Private Key %d: %s", i+1, newKeys.getPrivateKey()));
            System.out.println(String.format("CoSig Public Key %d: %s", i+1, newKeys.getPublicKey()));
            System.out.println(String.format("CoSig Address %d: %s", i+1, newAccount.getAddress()));
        }

        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        MultisigAggregateModificationTransaction multisigTransaction = new MultisigAggregateModificationTransaction(timeStamp, MultiSigAccount, coSigModColl, minCosigMod);
        //572500, 975000 Testnet Fee Fork Heights
        //875000, 1110000 Mainnet Fee Fork Heights
        multisigTransaction.setFee(new DefaultTransactionFeeCalculator(utilityFunc, () -> new BlockHeight(utilityFunc.getBlockHeight()), new BlockHeight(975000)).calculateMinimumFee(multisigTransaction));
        multisigTransaction.setDeadline(timeStamp.addHours(23));
        multisigTransaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(multisigTransaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", multisigTransaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);
    }

    private static String MultiSigTransaction(String coSigPrivKey, String multiSigPubKey, String receiverAddress, long amount, String message, String mosaicId, long mosaicQty) {
        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        Account senderAccount = new Account(new KeyPair(PrivateKey.fromHexString(coSigPrivKey)));
        Account multisigAccount = new Account(new KeyPair(PublicKey.fromHexString(multiSigPubKey)));
        Account recipientAccount = new Account(Address.fromEncoded(receiverAddress));

        TransferTransactionAttachment attachment = (message == null && mosaicId == null && mosaicQty != 0) ? null : new TransferTransactionAttachment();

        if (mosaicId != null && mosaicQty != 0) {
            MosaicId mosyId = MosaicId.parse(mosaicId);
            MosaicFeeInformation mosaicFeeInformation = utilityFunc.findById(mosyId);
            Double mosaicQuantityDouble = Double.valueOf(mosaicQty) * Math.pow(10, mosaicFeeInformation.getDivisibility());
            attachment.addMosaic(mosyId, Quantity.fromValue(mosaicQuantityDouble.longValue()));
        }

        if (message != null) {
            //PlainMessage for non encrypted messages, SecureMessage for encrypted messages. Fees calculated after encryption
            PlainMessage PM = new PlainMessage(message.getBytes());
            attachment.setMessage(PM);
        }

        TransferTransaction transaction = new TransferTransaction(timeStamp, multisigAccount, recipientAccount, Amount.fromNem(amount), attachment);
        //572500, 975000 Testnet Fee Fork Heights
        //875000, 1110000 Mainnet Fee Fork Heights
        DefaultTransactionFeeCalculator feeCalculator = new DefaultTransactionFeeCalculator(utilityFunc, () -> new BlockHeight(utilityFunc.getBlockHeight()), new BlockHeight(975000));
        transaction.setFee(feeCalculator.calculateMinimumFee(transaction));
        transaction.setDeadline(timeStamp.addHours(23));

        MultisigTransaction multisigTransaction = new MultisigTransaction(timeStamp, senderAccount, transaction);
        multisigTransaction.setFee(feeCalculator.calculateMinimumFee(multisigTransaction));
        multisigTransaction.setDeadline(timeStamp.addHours(23));
        multisigTransaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(multisigTransaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", multisigTransaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);
    }

    private static String CoSigTransaction(String coSigPrivKey, String multiSigAddress, String transactionHash) {
        TimeInstant timeStamp = new SystemTimeProvider().getCurrentTime();
        Account coSigAcc = new Account(new KeyPair(PrivateKey.fromHexString(coSigPrivKey)));
        Account multisigAccount = new Account(Address.fromEncoded(multiSigAddress));

        MultisigSignatureTransaction transaction = new MultisigSignatureTransaction(timeStamp, coSigAcc, multisigAccount, Hash.fromHexString(transactionHash));
        //572500, 975000 Testnet Fee Fork Heights
        //875000, 1110000 Mainnet Fee Fork Heights
        transaction.setFee(new DefaultTransactionFeeCalculator(utilityFunc, () -> new BlockHeight(utilityFunc.getBlockHeight()), new BlockHeight(975000)).calculateMinimumFee(transaction));
        transaction.setDeadline(timeStamp.addHours(23));
        transaction.sign();

        JSONObject paramsJSON = new JSONObject();
        final byte[] data = BinarySerializer.serializeToBytes(transaction.asNonVerifiable());
        paramsJSON.put("data", ByteUtils.toHexString(data));
        paramsJSON.put("signature", transaction.getSignature().toString());
        return utilityFunc.PostResults(API_ANNOUNCE_TRANSACTION, paramsJSON);
    }

    private static void GetAllConfirmedMessages(String addressToMonitor) {
        System.out.println("\nAll Confirmed Transactions");
        JSONArray resultJSONArr = null;
        JSONObject transactionJSON = null;
        long currTransactionID = 0;
        long lastTransactionID = 0;

        do {
            lastTransactionID = currTransactionID;
            String result = utilityFunc.GetResults(API_ALL_TRANSACTIONS, "address=" + addressToMonitor + ((lastTransactionID == 0) ? "" : "&id=" + lastTransactionID));
            resultJSONArr = JSONObject.fromObject(result).getJSONArray("data");
            for (int i = 0; i < resultJSONArr.size(); ++i) {
                transactionJSON = resultJSONArr.getJSONObject(i).getJSONObject("transaction");

                //Show only transactions with messages
                if (transactionJSON.containsKey("message")) {
                    utilityFunc.DecodeMessage(transactionJSON);
                    currTransactionID = resultJSONArr.getJSONObject(i).getJSONObject("meta").getLong("id");
                }
            }
        } while (lastTransactionID != currTransactionID);
    }

    private static void StartWebSocketService(String addressToMonitor) {
        //Web Socket monitor incoming messages in block chain
        List<Transport> transports = new ArrayList<Transport>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new StringMessageConverter());
        StompSessionHandler handler = new WSMonitorIncomingHandler(addressToMonitor);
        stompClient.connect(WSNEM_ADDRESS + "/w/messages", handler);
        System.out.println("Listening to messages...");

        Scanner scanner = new Scanner(System.in);
        try {
            while (!scanner.nextLine().equalsIgnoreCase("stop")) {
            }
        } catch (Exception e) {
            System.out.println(String.format("Exception caught: %s", e.getMessage()));
            e.printStackTrace();
        } finally {
            scanner.close();
            stompClient.stop();
        }
    }
}