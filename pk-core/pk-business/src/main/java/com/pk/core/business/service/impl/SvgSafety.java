package com.pk.core.business.service.impl;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chính sách SVG TĨNH: chỉ nhận SVG vẽ hình (logo, icon, nhãn), không cho script/animation/tham chiếu ngoài.
 * Từ chối: DOCTYPE/entity (XXE), thẻ script/foreignObject/iframe/object/embed/animate*, set, thuộc tính on*, href không phải "#id",
 * `javascript:`/`data:`/`vbscript:`, CSS @import hoặc url() ra ngoài, và processing instruction (vd xml-stylesheet).
 * Lúc phục vụ, file .svg còn được gắn Content-Security-Policy chặn mọi thứ chạy được (xem UploadWebConfig).
 */
public final class SvgSafety {

    public static final int MAX_BYTES = 512 * 1024;
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final Set<String> FORBIDDEN_ELEMENTS = Set.of("script", "foreignobject", "iframe", "object", "embed",
            "audio", "video", "canvas", "animate", "animatemotion", "animatetransform", "animatecolor", "set", "handler",
            "listener", "discard");
    private static final Pattern EXTERNAL_URL = Pattern.compile("url\\(\\s*['\"]?\\s*(?!#)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_VALUE = Pattern.compile("\\b(javascript|vbscript|data)\\s*:|expression\\s*\\(|@import|behavior\\s*:",
            Pattern.CASE_INSENSITIVE);

    private SvgSafety() {
    }

    /** Nội dung có hình dạng một file SVG (đủ để thử kiểm tra chặt hơn), tránh coi mọi file text là SVG. */
    public static boolean looksLikeSvg(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 2048), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return head.contains("<svg");
    }

    /** Ném 400 FILE_REJECTED (kèm lý do) nếu không an toàn. */
    public static void validate(byte[] data) {
        if (data.length > MAX_BYTES) {
            throw reject("SVG tối đa " + (MAX_BYTES / 1024) + "KB");
        }
        Document doc = parse(data);
        Element root = doc.getDocumentElement();
        if (!"svg".equals(root.getLocalName()) || !SVG_NS.equals(root.getNamespaceURI())) {
            throw reject("thẻ gốc phải là <svg xmlns=\"http://www.w3.org/2000/svg\">");
        }
        walk(doc);
    }

    private static Document parse(byte[] data) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            return b.parse(new ByteArrayInputStream(data));
        } catch (Exception ex) {
            throw reject("không đọc được SVG (XML sai cấu trúc hoặc có DOCTYPE/entity)");
        }
    }

    private static void walk(Node node) {
        for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE || c.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
                throw reject("không cho phép processing instruction / entity");
            }
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                checkElement((Element) c);
                walk(c);
            }
        }
    }

    private static void checkElement(Element e) {
        String name = (e.getLocalName() == null ? e.getTagName() : e.getLocalName()).toLowerCase(Locale.ROOT);
        if (FORBIDDEN_ELEMENTS.contains(name)) {
            throw reject("không cho phép thẻ <" + name + ">");
        }
        if ("style".equals(name)) {
            checkCss(e.getTextContent());
        }
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            checkAttribute((Attr) attrs.item(i));
        }
    }

    private static void checkAttribute(Attr a) {
        String name = (a.getLocalName() == null ? a.getName() : a.getLocalName()).toLowerCase(Locale.ROOT);
        String value = a.getValue() == null ? "" : a.getValue().trim();
        if (name.startsWith("on")) {
            throw reject("không cho phép thuộc tính sự kiện " + a.getName());
        }
        if ("href".equals(name) && !value.startsWith("#")) {
            throw reject("href chỉ được trỏ tới phần tử trong cùng file (#id)");
        }
        if ("style".equals(name)) {
            checkCss(value);
        } else if (DANGEROUS_VALUE.matcher(value).find()) {
            throw reject("giá trị thuộc tính " + a.getName() + " không được phép");
        }
    }

    private static void checkCss(String css) {
        String v = css == null ? "" : css;
        if (DANGEROUS_VALUE.matcher(v).find() || EXTERNAL_URL.matcher(v).find()) {
            throw reject("CSS trong SVG không được @import/url() ra ngoài hoặc chứa script");
        }
    }

    private static BusinessException reject(String reason) {
        return BusinessException.badRequest(ErrorCode.FILE_REJECTED, "SVG không an toàn: " + reason, reason);
    }
}
