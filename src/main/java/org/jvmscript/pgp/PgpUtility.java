package org.jvmscript.pgp;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.*;
import org.bouncycastle.util.io.Streams;

import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

import static org.jvmscript.log.LogUtility.logger;


/**
 * PgpUtility provides methods for generating PGP keys, encrypting and decrypting data.  The API is composed of two
 * layers, a streaming layer and a file oriented layer.  The streaming layer provides the greatest amount of flexibility
 * and is primarily intended for use in enterprise systems.  The file oriented layer provides the simplest interface and
 * is primarily intended for scripts.
 * <p>
 * The following example illustrates using the file oriented API.
 * <pre><code>
 *     generateKeyPairFiles("pub.asc", "priv.asc", "johndoe", "secret");
 *     encryptFile("sensitive.txt", "sensitive.dat", "pub.asc");
 *     decryptFile("sensitive.dat", "sensitive.txt", "priv.asc", "secret");
 * </code></pre>
 * <p>
 * The following example illustrates use of the streaming API.
 * <pre><code>
 *     PGPKeyPair keyPair = generateKeyPair(pubOut, privOut, "johndoe", "secret");
 *
 *     FileInputStream clearInput = new FileInputStream("example.txt");
 *     FileOutputStream cipherOutput = new FileOutputStream("example.dat");
 *     encryptStream(clearInput, cipherOutput, keyPair.getPublicKey());
 *
 *     FileInputStream cipherInput = new FileInputStream("example.dat");
 *     FileOutputStream clearOutput = new FileOutputStream("example2.txt");
 *     decryptStream(cipherInput, clearOutput, keyPair.getPrivateKey());
 * </code></pre>
 * <p>
 * The following example illustrates use of the streaming API to save a generated key pair to files.
 * <pre><code>
 *     PGPKeyPair keyPair = generateKeyPair(pubOut, privOut, "johndoe", "secret");
 *     FileOutputStream pubOutput = new FileOutputStream("pub.asc");
 *     FileOutputStream privOutput = new FileOutputStream("priv.asc");
 *     writeKeyPair(keyPair, pubOutput, privOutput);
 * </code></pre>
 */
public final class PgpUtility {
    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    private static final int DEFAULT_KEY_SIZE = 4096;


    public static void main(String[] args) {

        final String DIRECTORY = "/tmp/encrypt/";
        final String USER_PUBLIC_KEY = DIRECTORY + "user-pub.asc";
        final String USER_PRIVATE_KEY = DIRECTORY + "user-priv.asc";
        final String MASTER_PUBLIC_KEY = DIRECTORY + "master-pub.asc";
        final String MASTER_PRIVATE_KEY = DIRECTORY + "pgptest/master-priv.asc";

        generateKeyPairFiles(USER_PUBLIC_KEY, USER_PRIVATE_KEY, "johndoe", "secret");
        generateKeyPairFiles(MASTER_PUBLIC_KEY, MASTER_PRIVATE_KEY, "master", "mypass");
        encryptFile("/tmp/hello.txt", "/tmp/hello.dat", new String[] {USER_PUBLIC_KEY, MASTER_PUBLIC_KEY});
        decryptFile("/tmp/hello.dat", "/tmp/user-hello.txt", USER_PRIVATE_KEY, "secret");
        decryptFile("/tmp/hello.dat", "/tmp/master-hello.txt", MASTER_PRIVATE_KEY, "mypass");
    }


    private PgpUtility() {
        // static class
    }


    //
    // KEY MANAGEMENT
    //

    public static void generateKeyPairFiles(String publicKeyFileName, String privateKeyFileName, String keyUserId,
                                            String password) {
        generateKeyPairFiles(publicKeyFileName, privateKeyFileName, keyUserId, password, true);
    }

    public static void generateKeyPairFiles(String publicKeyFileName, String privateKeyFileName, String keyUserId,
                                            String password, boolean armor) {
        PGPKeyPair keyPair = generateKeyPair();
        writeKeyPairFile(keyPair, publicKeyFileName, privateKeyFileName, keyUserId, password, armor);
    }

    public static PGPKeyPair generateKeyPair() {
        PGPKeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", PROVIDER);
            keyPairGenerator.initialize(DEFAULT_KEY_SIZE);
            KeyPair jcaKeyPair = keyPairGenerator.generateKeyPair();
            keyPair = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, jcaKeyPair, new Date());
        } catch (NoSuchAlgorithmException | PGPException e) {
            throw new IllegalStateException(e);
        }
        return keyPair;
    }

    public static void writeKeyPairFile(PGPKeyPair keyPair, String publicKeyFileName, String privateKeyFileName,
                                        String keyUserId, String password, boolean armor) {
        try {
            OutputStream publicKeyOutputStream = new FileOutputStream(publicKeyFileName);
            OutputStream privateKeyOutputStream = new FileOutputStream(privateKeyFileName);
            writeKeyPair(keyPair, publicKeyOutputStream, privateKeyOutputStream, keyUserId, password, armor);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void writeKeyPair(PGPKeyPair keyPair, OutputStream publicKeyOutputStream,
                                    OutputStream privateKeyOutputStream, String keyUserId, String password,
                                    boolean armor) {
        if (armor) {
            publicKeyOutputStream = new ArmoredOutputStream(publicKeyOutputStream);
            privateKeyOutputStream = new ArmoredOutputStream(privateKeyOutputStream);
        }

        PGPSecretKey secretKey;
        try {
            PGPDigestCalculator digestCalculator = new JcaPGPDigestCalculatorProviderBuilder()
                    .setProvider(PROVIDER)
                    .build()
                    .get(HashAlgorithmTags.SHA1);
            PBESecretKeyEncryptor encryptor =
                    new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.CAST5, digestCalculator)
                            .setProvider(PROVIDER)
                            .build(password.toCharArray());
            JcaPGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                    keyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1).setProvider(PROVIDER);
            secretKey = new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION,
                    keyPair,
                    keyUserId,
                    digestCalculator,
                    null,
                    null,
                    signerBuilder,
                    encryptor);

            secretKey.encode(privateKeyOutputStream);
            secretKey.getPublicKey().encode(publicKeyOutputStream);
            // TODO should these be closed here or in writeKeyPairFile?
            privateKeyOutputStream.close();
            publicKeyOutputStream.close();
        } catch (PGPException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PGPPublicKey readPublicKeyFile(String fileName) {
        PGPPublicKey pubKey;
        try {
            InputStream keyIn = new BufferedInputStream(new FileInputStream(fileName));
            pubKey = readPublicKey(keyIn);
            keyIn.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return pubKey;
    }

    public static PGPPublicKey readPublicKey(InputStream inputStream) {
        PGPPublicKeyRingCollection pgpPub;
        try {
            InputStream decoderStream = PGPUtil.getDecoderStream(inputStream);
            JcaKeyFingerprintCalculator fingerPrintCalculator = new JcaKeyFingerprintCalculator();
            pgpPub = new PGPPublicKeyRingCollection(decoderStream, fingerPrintCalculator);
        } catch (IOException | PGPException e) {
            throw new IllegalStateException(e);
        }

        //
        // we just loop through the collection till we find a key suitable for encryption, in the real
        // world you would probably want to be a bit smarter about this.
        //

        Iterator<PGPPublicKeyRing> ringIter = pgpPub.getKeyRings();
        while (ringIter.hasNext()) {
            PGPPublicKeyRing ring = ringIter.next();
            Iterator<PGPPublicKey> keyIter = ring.getPublicKeys();
            while (keyIter.hasNext()) {
                PGPPublicKey key = keyIter.next();
                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }

        throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    public static List<PGPPrivateKey> readPrivateKey(String fileName, String password) {
        List<PGPPrivateKey> privateKeys = new ArrayList<>();
        try (
                FileInputStream keyInputStream = new FileInputStream(fileName);
                InputStream keyDecoderInputStream = PGPUtil.getDecoderStream(keyInputStream)
        ) {
            PGPSecretKeyRingCollection keyRings = new PGPSecretKeyRingCollection(keyDecoderInputStream, new JcaKeyFingerprintCalculator());
            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder()
                    .setProvider(PROVIDER)
                    .build(password.toCharArray());

            for (PGPSecretKeyRing keyRing : keyRings) {
                for (PGPSecretKey secretKey : keyRing) {
                    try {
                        PGPPrivateKey privateKey = secretKey.extractPrivateKey(keyDecryptor);
                        if (privateKey != null) {
                            privateKeys.add(privateKey);
                        }
                    } catch (PGPException e) {
                        logger.warn("Unable to extract private key for key ID {}: {}", secretKey.getKeyID(), e.getMessage());
                    }
                }
            }
        } catch (IOException | PGPException e) {
            throw new IllegalStateException("Error reading private key from file: " + fileName, e);
        }

        return privateKeys;
    }


    //
    // ENCRYPTION
    //


    public static void encryptFile(String inputFileName, String outputFileName, String publicKeyFileName) {
        encryptFile(inputFileName, outputFileName, new String[]{publicKeyFileName});
    }

    public static void encryptFile(String inputFileName, String outputFileName, String... publicKeyFileName) {
        encryptFile(inputFileName, outputFileName, publicKeyFileName, true, true);
    }

    public static void encryptFile(String inputFileName, String outputFileName, String[] publicKeyFileName,
                                   boolean armor, boolean integrityCheck) {
        InputStream inputStream;
        OutputStream outputStream;
        try {
            inputStream = new FileInputStream(inputFileName);
            outputStream = new BufferedOutputStream(new FileOutputStream(outputFileName));
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }

        PGPPublicKey[] publicKeys = new PGPPublicKey[publicKeyFileName.length];
        for (int i = 0; i < publicKeyFileName.length; i++) {
            publicKeys[i] = readPublicKeyFile(publicKeyFileName[i]);
        }

        encrypt(inputStream, inputFileName, outputStream, publicKeys, armor, integrityCheck);
    }


    public static void encrypt(InputStream inputStream, String inputFileName, OutputStream outputStream,
                               PGPPublicKey publicKey, boolean armor, boolean integrityCheck) {
        encrypt(inputStream, inputFileName, outputStream, new PGPPublicKey[]{publicKey}, armor, integrityCheck);
    }

    public static void encrypt(InputStream inputStream, String inputFileName, OutputStream outputStream,
                               PGPPublicKey[] publicKey, boolean armor, boolean integrityCheck) {
        if (armor) {
            outputStream = new ArmoredOutputStream(outputStream);
        }

        try {
            byte[] bytes = toLiteralZipByteArray(inputStream, inputFileName);
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                    new JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5)
                            .setWithIntegrityPacket(integrityCheck)
                            .setSecureRandom(new SecureRandom())
                            .setProvider(PROVIDER));

            for (int i = 0; i < publicKey.length; i++) {
                encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(publicKey[i]).setProvider(PROVIDER));
            }

            OutputStream encryptedOutputStream = encGen.open(outputStream, bytes.length);
            encryptedOutputStream.write(bytes);
            encryptedOutputStream.close();

            if (armor) {
                outputStream.close();
            }
        } catch (IOException | PGPException e) {
            throw new IllegalStateException(e);
        }
    }


    //
    // DECRYPTION
    //

    public static void decryptFile(String inputFileName, String outputFileName, String privateKeyFileName, String password) {
        try (
            InputStream inputStream = new FileInputStream(inputFileName);
            OutputStream outputStream = new FileOutputStream(outputFileName)
        ) {
            List<PGPPrivateKey> privateKeys = readPrivateKey(privateKeyFileName, password);
            decrypt(inputStream, outputStream, privateKeys);
        } catch (IOException e) {
            throw new IllegalStateException("File not found or IO error during decryption", e);
        }
    }

    public static void decrypt(InputStream dataInputStream, OutputStream dataOutputStream, List<PGPPrivateKey> privateKeys) {
        try (InputStream decodedStream = PGPUtil.getDecoderStream(dataInputStream)) {
            JcaPGPObjectFactory encryptedFactory = new JcaPGPObjectFactory(decodedStream);

            PGPEncryptedDataList encryptedDataList;
            Object firstObject = encryptedFactory.nextObject();

            if (firstObject instanceof PGPEncryptedDataList) {
                encryptedDataList = (PGPEncryptedDataList) firstObject;
            } else {
                encryptedDataList = (PGPEncryptedDataList) encryptedFactory.nextObject();
            }

            PGPPublicKeyEncryptedData encryptedData = null;
            PGPPrivateKey matchingPrivateKey = null;
            for (Iterator<?> it = encryptedDataList.getEncryptedDataObjects(); it.hasNext() && encryptedData == null; ) {
                PGPPublicKeyEncryptedData currentData = (PGPPublicKeyEncryptedData) it.next();
                for (PGPPrivateKey privateKey : privateKeys) {
                    if (privateKey.getKeyID() == currentData.getKeyID()) {
                        matchingPrivateKey = privateKey;
                        encryptedData = currentData;
                        break;
                    }
                }
            }

            if (encryptedData == null) {
                throw new IllegalStateException("Cannot decrypt input with provided keys.");
            }

            try (InputStream clearDataStream = encryptedData.getDataStream(
                    new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(PROVIDER).build(matchingPrivateKey));
                 OutputStream outStream = dataOutputStream) {

                JcaPGPObjectFactory decryptedFactory = new JcaPGPObjectFactory(clearDataStream);
                Object message = decryptedFactory.nextObject();

                if (message instanceof PGPCompressedData) {
                    JcaPGPObjectFactory compressedFactory = new JcaPGPObjectFactory(((PGPCompressedData) message).getDataStream());
                    message = compressedFactory.nextObject();
                }

                if (message instanceof PGPLiteralData) {
                    try (InputStream literalDataStream = ((PGPLiteralData) message).getInputStream()) {
                        Streams.pipeAll(literalDataStream, outStream);
                    }
                } else if (message instanceof PGPOnePassSignatureList) {
                    throw new PGPException("Encrypted message contains a signed message; expected literal data.");
                } else {
                    throw new PGPException("Unknown message type; expected simple encrypted file.");
                }

                if (encryptedData.isIntegrityProtected() && !encryptedData.verify()) {
                    System.err.println("Warning: message failed integrity check");
                    logger.warn("message failed integrity check");
                } else {
                    logger.info("message integrity check passed");
                }
            }
        } catch (PGPException | IOException e) {
            throw new IllegalStateException("Error during decryption process", e);
        }
    }


    //
    // ENCODING
    //

    private static byte[] toLiteralZipByteArray(InputStream in, String fileName)
            throws IOException {
        return toLiteralZipByteArray(in, fileName, new Date());
    }

    private static byte[] toLiteralZipByteArray(InputStream in, String fileName, Date lastModified)
            throws IOException {
        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        OutputStream outputStream = comData.open(bOut);
        writeStreamToLiteralData(outputStream, in, fileName, lastModified);
        comData.close();
        return bOut.toByteArray();
    }

    private static void writeStreamToLiteralData(OutputStream outputStream, InputStream inputStream, String fileName,
                                                 Date lastModified) {
        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
        try {
            byte[] bytes = IOUtils.toByteArray(inputStream);
            OutputStream pOut = lData.open(outputStream, PGPLiteralDataGenerator.BINARY, fileName, bytes.length,
                    lastModified);
            IOUtils.write(bytes, pOut);
            pOut.close();
            inputStream.close(); // TODO should this be closed here?
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}