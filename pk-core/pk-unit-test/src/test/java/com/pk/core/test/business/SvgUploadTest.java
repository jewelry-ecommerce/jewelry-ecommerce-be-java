package com.pk.core.test.business;

import com.pk.core.business.storage.UploadMediaKind;
import com.pk.core.business.storage.UploadMediaValidator;
import com.pk.core.business.service.impl.LocalFileStorageService;
import com.pk.core.business.service.impl.UploadStorage;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SvgUploadTest {
    private static final String OPEN = "<svg xmlns='http://www.w3.org/2000/svg'>";
    private static final String CLOSE = "</svg>";
    private final UploadMediaValidator validator = new UploadMediaValidator(10485760);
    @TempDir Path temporaryDirectory;

    @Test
    void acceptsActualHeaderLogoFixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/upload/header-logo.svg")) {
            assertNotNull(input);
            assertEquals(UploadMediaKind.SVG, validator.validate(
                    new MockMultipartFile("file", "header-logo.svg", "image/svg+xml", input.readAllBytes())));
        }
    }

    @Test
    void acceptsXmlDeclarationBomGradientsAndLocalReferences() {
        String svg = "\uFEFF<?xml version='1.0' encoding='UTF-8'?>" + OPEN
                + "<defs><linearGradient id='gold'><stop offset='0' stop-color='#fff'/></linearGradient>"
                + "<path id='logo' d='M0 0 L10 10'/></defs>"
                + "<use href='#logo' style='fill:url(#gold);stroke:#333;stroke-width:2'/>" + CLOSE;
        assertEquals(UploadMediaKind.SVG, validator.validate(svg(svg)));
    }

    @Test
    void acceptsExtensionWhenBrowserUsesOctetStream() {
        assertEquals(UploadMediaKind.SVG, validator.validate(new MockMultipartFile(
                "file", "LOGO.SVG", "application/octet-stream", (OPEN + CLOSE).getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void localStoragePreservesSvgBytesAndMime() throws Exception {
        String contents = OPEN + "<path d='M0 0 L10 10' fill='#333'/>" + CLOSE;
        var storage = new LocalFileStorageService(validator,
                new UploadStorage(temporaryDirectory.toString(), "https://example.test"));
        var response = storage.store(svg(contents));
        assertEquals("image/svg+xml", response.getContentType());
        assertEquals("IMAGE", response.getType());
        assertTrue(response.getFileName().endsWith(".svg"));
        try (var paths = Files.walk(temporaryDirectory)) {
            assertEquals(contents, Files.readString(paths.filter(Files::isRegularFile).findFirst().orElseThrow()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert(1)</script>",
        "<g onload='alert(1)'/>",
        "<foreignObject><div xmlns='http://www.w3.org/1999/xhtml'>x</div></foreignObject>",
        "<image href='https://outside.test/image.png'/>",
        "<use href='https://outside.test/logo.svg#logo'/>",
        "<use href='data:image/svg+xml,evil'/>",
        "<animate attributeName='href' values='javascript:alert(1)'/>",
        "<style>@import 'https://outside.test/style.css';</style>",
        "<path style='fill:url(https://outside.test/paint.svg)'/>",
        "<path style='-moz-binding:url(#binding)'/>",
        "<g xmlns='http://www.w3.org/1999/xhtml'/>"
    })
    void rejectsActiveContentAndExternalResources(String content) {
        BusinessException error = assertThrows(BusinessException.class, () -> validator.validate(svg(OPEN + content + CLOSE)));
        assertEquals(400, error.getStatus());
        assertEquals(ErrorCode.FILE_REJECTED, error.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<!DOCTYPE svg SYSTEM 'https://outside.test/evil.dtd'><svg xmlns='http://www.w3.org/2000/svg'/>",
        "<!DOCTYPE svg [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><svg xmlns='http://www.w3.org/2000/svg'>&secret;</svg>",
        "<?xml-stylesheet type='text/css' href='https://outside.test/style.css'?><svg xmlns='http://www.w3.org/2000/svg'/>",
        "<svg xmlns='http://www.w3.org/2000/svg'><path></svg>",
        "<html xmlns='http://www.w3.org/2000/svg'/>",
        "<svg/>",
        "<script>alert(1)</script>",
        ""
    })
    void rejectsDoctypeEntitiesMalformedAndNonSvg(String contents) {
        assertThrows(BusinessException.class, () -> validator.validate(svg(contents)));
    }

    @Test
    void rejectsExcessiveNestingAndOversizeSvg() {
        String nested = OPEN + "<g>".repeat(129) + "</g>".repeat(129) + CLOSE;
        assertThrows(BusinessException.class, () -> validator.validate(svg(nested)));
        String oversized = OPEN + " ".repeat(512 * 1024) + CLOSE;
        assertEquals(ErrorCode.FILE_REJECTED,
                assertThrows(BusinessException.class, () -> validator.validate(svg(oversized))).getCode());
        assertThrows(BusinessException.class, () -> new UploadMediaValidator(4).validate(svg(OPEN + CLOSE)));
    }

    private MockMultipartFile svg(String contents) {
        return new MockMultipartFile("file", "logo.svg", "image/svg+xml", contents.getBytes(StandardCharsets.UTF_8));
    }
}
