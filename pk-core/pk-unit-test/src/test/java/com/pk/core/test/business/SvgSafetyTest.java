package com.pk.core.test.business;

import com.pk.core.business.service.impl.SvgSafety;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chính sách SVG tĩnh: nhận logo/icon bình thường, từ chối script, sự kiện, tham chiếu ngoài, DOCTYPE/entity, CSS ra ngoài. */
class SvgSafetyTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void accepted(String svg) {
        assertDoesNotThrow(() -> SvgSafety.validate(b(svg)), svg);
    }

    private static void rejected(String svg) {
        BusinessException ex = assertThrows(BusinessException.class, () -> SvgSafety.validate(b(svg)), svg);
        assertEquals(ErrorCode.FILE_REJECTED, ex.getCode());
        assertEquals(400, ex.getStatus());
    }

    @Test
    void staticLogoIsAccepted() {
        accepted("<?xml version=\"1.0\" encoding=\"UTF-8\"?><svg " + NS + " viewBox=\"0 0 10 10\"><g fill=\"#c00\"><path d=\"M0 0L10 10\"/>"
                + "<circle cx=\"5\" cy=\"5\" r=\"2\" style=\"fill:url(#g)\"/></g><defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"red\"/>"
                + "</linearGradient></defs><use href=\"#g\"/><text>metadata: ok</text></svg>");
        accepted("<svg " + NS + "><style>.a{fill:#fff;stroke:url(#x)}</style><rect class=\"a\"/></svg>");
    }

    @Test
    void scriptsEventsAndAnimationsAreRejected() {
        rejected("<svg " + NS + "><script>alert(1)</script></svg>");
        rejected("<svg " + NS + " xmlns:x=\"http://www.w3.org/1999/xhtml\"><x:script>1</x:script></svg>");
        rejected("<svg " + NS + " onload=\"alert(1)\"/>");
        rejected("<svg " + NS + "><rect onClick=\"x()\"/></svg>");
        rejected("<svg " + NS + "><foreignObject><div/></foreignObject></svg>");
        rejected("<svg " + NS + "><iframe src=\"x\"/></svg>");
        rejected("<svg " + NS + "><rect><animate attributeName=\"x\" to=\"1\"/></rect></svg>");
        rejected("<svg " + NS + "><a><set attributeName=\"href\" to=\"javascript:alert(1)\"/></a></svg>");
    }

    @Test
    void externalReferencesAndDangerousValuesAreRejected() {
        rejected("<svg " + NS + "><a href=\"javascript:alert(1)\"><rect/></a></svg>");
        rejected("<svg " + NS + "><image href=\"http://evil/x.png\"/></svg>");
        rejected("<svg " + NS + "><image href=\"data:image/png;base64,AAAA\"/></svg>");
        rejected("<svg " + NS + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><use xlink:href=\"http://evil/a.svg#x\"/></svg>");
        rejected("<svg " + NS + "><style>@import url(http://evil/x.css);</style></svg>");
        rejected("<svg " + NS + "><rect style=\"fill:url(http://evil/x)\"/></svg>");
        rejected("<svg " + NS + "><style>.a{background:url( 'https://evil/x.png')}</style></svg>");
        rejected("<svg " + NS + "><rect style=\"width:expression(alert(1))\"/></svg>");
        rejected("<svg " + NS + "><rect fill=\"javascript:alert(1)\"/></svg>");
    }

    @Test
    void xxeDoctypeProcessingInstructionAndBadStructureAreRejected() {
        rejected("<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg " + NS + "><text>&x;</text></svg>");
        rejected("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\"><svg " + NS + "/>");
        rejected("<?xml-stylesheet href=\"http://evil/x.css\"?><svg " + NS + "/>");
        rejected("<svg><rect/></svg>");
        rejected("<html " + NS + "/>");
        rejected("<svg " + NS + "><g></svg>");
    }

    @Test
    void oversizedSvgIsRejected() {
        rejected("<svg " + NS + "><!-- " + "x".repeat(600 * 1024) + " --></svg>");
    }

    @Test
    void looksLikeSvgOnlyForSvgText() {
        assertTrue(SvgSafety.looksLikeSvg(b("  <?xml version=\"1.0\"?><svg/>")));
        assertFalse(SvgSafety.looksLikeSvg(b("<html><body>x</body></html>")));
    }
}
