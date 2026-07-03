package org.jvmscript.pgp;

import org.apache.commons.io.FilenameUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;


/**
 * PgpUtility provides methods for generating PGP keys, encrypting and decrypting data.  The API is composed of two
 * layers, a streaming layer and a file oriented layer.  The streaming layer provides the greatest amount of flexibility
 * and is primarily intended for use in enterprise systems.  The file oriented layer provides the simplest interface and
 * is primarily intended for scripts.
 * <p>
 * Data is encrypted with AES-256 and an integrity (MDC) packet by default.  Decryption accepts both plain encrypted
 * and sign-then-encrypt messages (signatures are skipped, not verified).  A failed integrity check throws and, in the
 * file oriented layer, removes the output file.
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
 *     PGPKeyPair keyPair = generateKeyPair();
 *
 *     FileInputStream clearInput = new FileInputStream("example.txt");
 *     FileOutputStream cipherOutput = new FileOutputStream("example.dat");
 *     encrypt(clearInput, "example.txt", cipherOutput, keyPair.getPublicKey(), true, true);
 * </code></pre>
 */
public final class PgpUtility {
    private static final Logger logger = LoggerFactory.getLogger(PgpUtility.class);
    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    private static final int DEFAULT_KEY_SIZE = 4096;
    private static final int BUFFER_SIZE = 1 << 16;


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
        try (OutputStream publicKeyOutputStream = new FileOutputStream(publicKeyFileName);
             OutputStream privateKeyOutputStream = new FileOutputStream(privateKeyFileName)) {
            writeKeyPair(keyPair, publicKeyOutputStream, privateKeyOutputStream, keyUserId, password, armor);
        } catch (IOException e) {
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
            //the SHA1 digest calculator is the OpenPGP secret key checksum, fixed by the spec - not a signature hash
            PGPDigestCalculator digestCalculator = new JcaPGPDigestCalculatorProviderBuilder()
                    .setProvider(PROVIDER)
                    .build()
                    .get(HashAlgorithmTags.SHA1);
            PBESecretKeyEncryptor encryptor =
                    new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, digestCalculator)
                            .setProvider(PROVIDER)
                            .build(password.toCharArray());
            JcaPGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                    keyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256).setProvider(PROVIDER);
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
            privateKeyOutputStream.close();
            publicKeyOutputStream.close();
        } catch (PGPException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PGPPublicKey readPublicKeyFile(String fileName) {
        try (InputStream keyIn = new BufferedInputStream(new FileInputStream(fileName))) {
            return readPublicKey(keyIn);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
        PGPPublicKey[] publicKeys = new PGPPublicKey[publicKeyFileName.length];
        for (int i = 0; i < publicKeyFileName.length; i++) {
            publicKeys[i] = readPublicKeyFile(publicKeyFileName[i]);
        }

        try (InputStream inputStream = new FileInputStream(inputFileName);
             OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFileName))) {
            encrypt(inputStream, inputFileName, outputStream, publicKeys, armor, integrityCheck);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    public static void encrypt(InputStream inputStream, String inputFileName, OutputStream outputStream,
                               PGPPublicKey publicKey, boolean armor, boolean integrityCheck) {
        encrypt(inputStream, inputFileName, outputStream, new PGPPublicKey[]{publicKey}, armor, integrityCheck);
    }

    /**
     * Encrypts {@code inputStream} to {@code outputStream} for the given recipients, streaming - the input is never
     * fully buffered in memory.  Closes the wrappers it creates but not the caller's streams; the caller is
     * responsible for closing both streams.
     */
    public static void encrypt(InputStream inputStream, String inputFileName, OutputStream outputStream,
                               PGPPublicKey[] publicKey, boolean armor, boolean integrityCheck) {
        OutputStream armoredStream = armor ? new ArmoredOutputStream(outputStream) : outputStream;

        try {
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                    new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                            .setWithIntegrityPacket(integrityCheck)
                            .setSecureRandom(new SecureRandom())
                            .setProvider(PROVIDER));

            for (PGPPublicKey key : publicKey) {
                encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(PROVIDER));
            }

            OutputStream encryptedStream = encGen.open(armoredStream, new byte[BUFFER_SIZE]);
            PGPCompressedDataGenerator compressedGenerator = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
            OutputStream compressedStream = compressedGenerator.open(encryptedStream);

            //embed only the base filename in the literal data packet, not the local path
            PGPLiteralDataGenerator literalGenerator = new PGPLiteralDataGenerator();
            OutputStream literalStream = literalGenerator.open(compressedStream, PGPLiteralDataGenerator.BINARY,
                    FilenameUtils.getName(inputFileName), new Date(), new byte[BUFFER_SIZE]);

            Streams.pipeAll(inputStream, literalStream);

            literalStream.close();
            compressedGenerator.close();
            encryptedStream.close();

            if (armor) {
                armoredStream.close();
            }
            outputStream.flush();
        } catch (IOException | PGPException e) {
            throw new IllegalStateException(e);
        }
    }


    //
    // DECRYPTION
    //

    public static void decryptFile(String inputFileName, String outputFileName, String privateKeyFileName, String password) {
        try {
            try (
                InputStream inputStream = new FileInputStream(inputFileName);
                OutputStream outputStream = new FileOutputStream(outputFileName)
            ) {
                List<PGPPrivateKey> privateKeys = readPrivateKey(privateKeyFileName, password);
                decrypt(inputStream, outputStream, privateKeys);
            }
        } catch (Exception e) {
            //never leave partial or tampered output behind for downstream jobs to pick up
            try {
                Files.deleteIfExists(Path.of(outputFileName));
            } catch (IOException cleanupFailure) {
                logger.warn("Could not remove partial output file {}: {}", outputFileName, cleanupFailure.getMessage());
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
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

                JcaPGPObjectFactory plainFactory = new JcaPGPObjectFactory(clearDataStream);
                Object message = plainFactory.nextObject();

                //descend into compressed data and skip signature packets - partners commonly
                //sign-then-encrypt; the signature is skipped, not verified
                while (!(message instanceof PGPLiteralData)) {
                    if (message instanceof PGPCompressedData) {
                        plainFactory = new JcaPGPObjectFactory(((PGPCompressedData) message).getDataStream());
                        message = plainFactory.nextObject();
                    } else if (message instanceof PGPOnePassSignatureList || message instanceof PGPSignatureList) {
                        message = plainFactory.nextObject();
                    } else {
                        throw new PGPException("Unknown message type; expected literal data but found " +
                                (message == null ? "end of message" : message.getClass().getSimpleName()));
                    }
                }

                try (InputStream literalDataStream = ((PGPLiteralData) message).getInputStream()) {
                    Streams.pipeAll(literalDataStream, outStream);
                }

                if (encryptedData.isIntegrityProtected() && !encryptedData.verify()) {
                    throw new PGPException("Message failed integrity check - the data may have been tampered with or corrupted");
                }
                logger.info("message integrity check passed");
            }
        } catch (PGPException | IOException e) {
            throw new IllegalStateException("Error during decryption process", e);
        }
    }
}
