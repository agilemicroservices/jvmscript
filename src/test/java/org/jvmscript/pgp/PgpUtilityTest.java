package org.jvmscript.pgp;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class PgpUtilityTest {

    private static final String CONTENT = "clOrdId,side,lastQty\nORD-1,BUY,100\nORD-2,SELL,250\n";

    //key generation is slow (RSA 4096) - generate two pairs once for all tests
    @TempDir
    static Path keyDir;
    static String userPublicKey, userPrivateKey, otherPublicKey, otherPrivateKey;

    @TempDir
    Path workDir;

    @BeforeAll
    static void generateKeys() {
        userPublicKey = keyDir.resolve("user-pub.asc").toString();
        userPrivateKey = keyDir.resolve("user-priv.asc").toString();
        otherPublicKey = keyDir.resolve("other-pub.asc").toString();
        otherPrivateKey = keyDir.resolve("other-priv.asc").toString();
        PgpUtility.generateKeyPairFiles(userPublicKey, userPrivateKey, "user", "secret");
        PgpUtility.generateKeyPairFiles(otherPublicKey, otherPrivateKey, "other", "otherpass");
    }

    private Path writeInputFile() throws Exception {
        Path input = workDir.resolve("input.csv");
        Files.writeString(input, CONTENT);
        return input;
    }

    @Test
    public void armoredRoundTripRestoresOriginalContent() throws Exception {
        Path input = writeInputFile();
        Path encrypted = workDir.resolve("input.csv.pgp");
        Path decrypted = workDir.resolve("output.csv");

        PgpUtility.encryptFile(input.toString(), encrypted.toString(), userPublicKey);
        PgpUtility.decryptFile(encrypted.toString(), decrypted.toString(), userPrivateKey, "secret");

        assertEquals(CONTENT, Files.readString(decrypted));
        assertTrue(Files.readString(encrypted).contains("BEGIN PGP MESSAGE"));
    }

    @Test
    public void unarmoredRoundTripRestoresOriginalContent() throws Exception {
        Path input = writeInputFile();
        Path encrypted = workDir.resolve("input.csv.pgp");
        Path decrypted = workDir.resolve("output.csv");

        PgpUtility.encryptFile(input.toString(), encrypted.toString(),
                new String[]{userPublicKey}, false, true);
        PgpUtility.decryptFile(encrypted.toString(), decrypted.toString(), userPrivateKey, "secret");

        assertEquals(CONTENT, Files.readString(decrypted));
    }

    @Test
    public void multipleRecipientsCanEachDecrypt() throws Exception {
        Path input = writeInputFile();
        Path encrypted = workDir.resolve("input.csv.pgp");

        PgpUtility.encryptFile(input.toString(), encrypted.toString(), userPublicKey, otherPublicKey);

        Path decryptedByUser = workDir.resolve("user-output.csv");
        PgpUtility.decryptFile(encrypted.toString(), decryptedByUser.toString(), userPrivateKey, "secret");
        assertEquals(CONTENT, Files.readString(decryptedByUser));

        Path decryptedByOther = workDir.resolve("other-output.csv");
        PgpUtility.decryptFile(encrypted.toString(), decryptedByOther.toString(), otherPrivateKey, "otherpass");
        assertEquals(CONTENT, Files.readString(decryptedByOther));
    }

    @Test
    public void wrongPrivateKeyFailsAndLeavesNoOutputFile() throws Exception {
        Path input = writeInputFile();
        Path encrypted = workDir.resolve("input.csv.pgp");
        Path decrypted = workDir.resolve("output.csv");

        PgpUtility.encryptFile(input.toString(), encrypted.toString(), userPublicKey);

        var exception = assertThrows(IllegalStateException.class, () ->
                PgpUtility.decryptFile(encrypted.toString(), decrypted.toString(), otherPrivateKey, "otherpass"));
        assertTrue(exception.getMessage().contains("Cannot decrypt"));
        assertFalse(Files.exists(decrypted), "failed decryption must not leave an output file behind");
    }

    @Test
    public void tamperedCiphertextFailsAndLeavesNoOutputFile() throws Exception {
        Path input = writeInputFile();
        Path encrypted = workDir.resolve("input.csv.pgp");
        Path decrypted = workDir.resolve("output.csv");

        PgpUtility.encryptFile(input.toString(), encrypted.toString(),
                new String[]{userPublicKey}, false, true);

        //flip one bit near the end of the ciphertext (inside the encrypted data / MDC region)
        byte[] bytes = Files.readAllBytes(encrypted);
        bytes[bytes.length - 12] ^= 0x01;
        Files.write(encrypted, bytes);

        assertThrows(IllegalStateException.class, () ->
                PgpUtility.decryptFile(encrypted.toString(), decrypted.toString(), userPrivateKey, "secret"));
        assertFalse(Files.exists(decrypted), "tampered decryption must not leave an output file behind");
    }

    @Test
    public void signedAndEncryptedMessageDecrypts() throws Exception {
        Path encrypted = workDir.resolve("signed.pgp");
        Path decrypted = workDir.resolve("output.csv");

        PGPPublicKey encryptionKey = PgpUtility.readPublicKeyFile(userPublicKey);
        PGPPrivateKey signingKey = PgpUtility.readPrivateKey(otherPrivateKey, "otherpass").get(0);
        int signingAlgorithm = PgpUtility.readPublicKeyFile(otherPublicKey).getAlgorithm();

        try (OutputStream out = Files.newOutputStream(encrypted)) {
            encryptSignThenEncrypt(CONTENT.getBytes(), "input.csv", out, encryptionKey, signingKey, signingAlgorithm);
        }

        PgpUtility.decryptFile(encrypted.toString(), decrypted.toString(), userPrivateKey, "secret");
        assertEquals(CONTENT, Files.readString(decrypted));
    }

    //builds a sign-then-encrypt message (one-pass signature + literal data + signature,
    //compressed, then encrypted) - the layout partners' GnuPG "gpg -se" produces
    private static void encryptSignThenEncrypt(byte[] data, String filename, OutputStream out,
                                               PGPPublicKey encryptionKey, PGPPrivateKey signingKey,
                                               int signingAlgorithm) throws Exception {
        var provider = new BouncyCastleProvider();

        PGPEncryptedDataGenerator encryptedGenerator = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
                        .setProvider(provider));
        encryptedGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encryptionKey).setProvider(provider));
        OutputStream encryptedOut = encryptedGenerator.open(out, new byte[4096]);

        PGPCompressedDataGenerator compressedGenerator = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
        OutputStream compressedOut = compressedGenerator.open(encryptedOut);

        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(signingAlgorithm, HashAlgorithmTags.SHA256).setProvider(provider));
        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, signingKey);
        signatureGenerator.generateOnePassVersion(false).encode(compressedOut);

        PGPLiteralDataGenerator literalGenerator = new PGPLiteralDataGenerator();
        OutputStream literalOut = literalGenerator.open(compressedOut, PGPLiteralDataGenerator.BINARY,
                filename, data.length, new Date());
        literalOut.write(data);
        signatureGenerator.update(data);
        literalGenerator.close();

        signatureGenerator.generate().encode(compressedOut);
        compressedGenerator.close();
        encryptedOut.close();
    }
}
