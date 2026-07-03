package org.jvmscript.property;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PropertyUtilityTest {

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void setUp() {
        PropertyUtility.propertyInitialize();
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("sftp.password");
        PropertyUtility.propertyInitialize();
    }

    @Test
    public void environmentNameMappingUppercasesAndReplacesNonAlphanumerics() {
        assertEquals("SFTP_PASSWORD", PropertyUtility.toEnvironmentName("sftp.password"));
        assertEquals("SFTP_HOSTKEY_VERIFY", PropertyUtility.toEnvironmentName("sftp.hostkey.verify"));
        assertEquals("MY_SERVER_URL", PropertyUtility.toEnvironmentName("my-server.url"));
    }

    @Test
    public void environmentVariableWinsOverSystemPropertyAndFile() {
        environment.put("SFTP_PASSWORD", "fromEnvironment");
        System.setProperty("sftp.password", "fromSystemProperty");
        PropertyUtility.propertyPut("sftp.password", "fromFile");

        assertEquals("fromEnvironment", PropertyUtility.resolveProperty("sftp.password", environment));
    }

    @Test
    public void systemPropertyWinsOverFileWhenNoEnvironmentVariable() {
        System.setProperty("sftp.password", "fromSystemProperty");
        PropertyUtility.propertyPut("sftp.password", "fromFile");

        assertEquals("fromSystemProperty", PropertyUtility.resolveProperty("sftp.password", environment));
    }

    @Test
    public void fileValueIsFallbackWhenNothingElseSet() {
        PropertyUtility.propertyPut("sftp.password", "fromFile");

        assertEquals("fromFile", PropertyUtility.resolveProperty("sftp.password", environment));
    }

    @Test
    public void returnsNullWhenPropertyNotDefinedAnywhere() {
        assertNull(PropertyUtility.resolveProperty("sftp.password", environment));
    }

    @Test
    public void worksWithoutAnyPropertyFileLoaded() {
        PropertyUtility.propertyInitialize();
        environment.put("SFTP_PASSWORD", "fromEnvironment");

        assertEquals("fromEnvironment", PropertyUtility.resolveProperty("sftp.password", environment));
    }

    @Test
    public void fileValueEnvPlaceholderIsResolved() {
        environment.put("SFTP_PROD_PASSWORD", "secretValue");
        PropertyUtility.propertyPut("sftp.password", "${ENV:SFTP_PROD_PASSWORD}");

        assertEquals("secretValue", PropertyUtility.resolveProperty("sftp.password", environment));
    }

    @Test
    public void fileValueWithMultiplePlaceholdersAndLiteralTextIsResolved() {
        environment.put("DB_USER", "appuser");
        environment.put("DB_PASSWORD", "secret");
        PropertyUtility.propertyPut("db.credentials", "${ENV:DB_USER}:${ENV:DB_PASSWORD}@prod");

        assertEquals("appuser:secret@prod", PropertyUtility.resolveProperty("db.credentials", environment));
    }

    @Test
    public void missingPlaceholderEnvironmentVariableThrowsWithClearMessage() {
        PropertyUtility.propertyPut("sftp.password", "${ENV:MISSING_SECRET}");

        var exception = assertThrows(IllegalStateException.class,
                () -> PropertyUtility.resolveProperty("sftp.password", environment));
        assertTrue(exception.getMessage().contains("MISSING_SECRET"));
        assertTrue(exception.getMessage().contains("sftp.password"));
    }

    @Test
    public void fileValueWithoutPlaceholderIsReturnedVerbatim() {
        PropertyUtility.propertyPut("sftp.server", "sftp.example.com");

        assertEquals("sftp.example.com", PropertyUtility.resolveProperty("sftp.server", environment));
    }

    @Test
    public void missingDefaultPropertyFileIsToleratedAndEnvironmentStillResolves() throws Exception {
        PropertyUtility.propertyOpenFileClassPath(PropertyUtility.DEFAULT_PROPERTY_FILE);

        environment.put("SFTP_PASSWORD", "fromEnvironment");
        assertEquals("fromEnvironment", PropertyUtility.resolveProperty("sftp.password", environment));
        assertNull(PropertyUtility.resolveProperty("sftp.server", environment));
    }

    @Test
    public void missingExplicitlyNamedPropertyFileThrows() {
        var exception = assertThrows(java.io.FileNotFoundException.class,
                () -> PropertyUtility.propertyOpenFileClassPath("no-such-file.properties"));
        assertTrue(exception.getMessage().contains("no-such-file.properties"));
    }
}
