package com.pk.core.test.business;

import com.pk.core.business.service.impl.CloudinaryFileStorageService;
import com.pk.core.business.storage.*;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CloudinaryStorageTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final String ENDPOINT = "https://api.cloudinary.com/v1_1/test-cloud/image/upload";
    private MockRestServiceServer server;
    private CloudinaryFileStorageService storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryStorageProperties properties = new CloudinaryStorageProperties();
        properties.setCloudName("test-cloud");
        properties.setApiKey("test-api-key");
        properties.setApiSecret("test-api-secret");
        storage = new CloudinaryFileStorageService(properties, new UploadMediaValidator(1024), builder.build());
    }

    @Test
    void signatureUsesSortedFieldsAndSha256() {
        assertEquals("f5634837f56a287e0d8b74bcf33a775eaad9d29e4d5f07c8ea4cfb206abe91b0",
                CloudinaryUploadSigner.sign("jewelry-commerce", "sample", "1700000000", "secret"));
    }

    @Test
    void signedUploadKeepsFrontendContractAndNeverSendsSecret() {
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST)).andExpect(request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString();
            assertTrue(body.contains("name=\"signature\""));
            assertTrue(body.contains("name=\"timestamp\""));
            assertTrue(body.contains("name=\"public_id\""));
            assertTrue(body.contains("image/png"));
            assertFalse(body.contains("test-api-secret"));
            assertFalse(body.contains("signature_algorithm"));
        }).andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/a.png\","
                + "\"extra\":\"ignored\"}", MediaType.APPLICATION_JSON));
        var response = storage.store(image());
        assertEquals("IMAGE", response.getType());
        assertEquals("image/png", response.getContentType());
        assertEquals("image.png", response.getOriginalName());
        assertEquals(PNG.length, response.getSize());
        assertTrue(response.getUrl().startsWith("https://res.cloudinary.com/"));
        assertTrue(response.getFileName().endsWith(".png"));
        server.verify();
    }

    @Test
    void fakeImageIsRejectedBeforeNetwork() {
        BusinessException error = assertThrows(BusinessException.class, () -> storage.store(
                new MockMultipartFile("file", "image.png", "image/png", "<script>x</script>".getBytes())));
        assertEquals(400, error.getStatus());
        server.verify();
    }

    @Test
    void providerErrorReturnsStable503WithoutExposingCredentials() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("test-api-secret diagnostic"));
        BusinessException error = assertThrows(BusinessException.class, () -> storage.store(image()));
        assertEquals(503, error.getStatus());
        assertEquals(ErrorCode.UPLOAD_STORAGE_UNAVAILABLE, error.getCode());
        assertFalse(error.getMessage().contains("test-api-secret"));
        server.verify();
    }

    @Test
    void unsafeDeliveryUrlIsRejected() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                "{\"secure_url\":\"http://localhost/private\"}", MediaType.APPLICATION_JSON));
        assertEquals(503, assertThrows(BusinessException.class, () -> storage.store(image())).getStatus());
        server.verify();
    }

    @Test
    void emptyProviderResponseIsNotSuccess() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertEquals(503, assertThrows(BusinessException.class, () -> storage.store(image())).getStatus());
        server.verify();
    }

    @Test
    void videoUsesSeparateResourceEndpoint() {
        byte[] mp4 = {0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/video/upload"))
                .andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/test-cloud/video/upload/a.mp4\"}",
                        MediaType.APPLICATION_JSON));
        assertEquals("VIDEO", storage.store(new MockMultipartFile("file", "a.mp4", "video/mp4", mp4)).getType());
        server.verify();
    }

    @Test
    void missingAndOversizeFilesAreRejected() {
        UploadMediaValidator validator = new UploadMediaValidator(4);
        assertThrows(BusinessException.class, () -> validator.validate(null));
        assertThrows(BusinessException.class, () -> validator.validate(image()));
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "image.png", "image/png", PNG);
    }
}
