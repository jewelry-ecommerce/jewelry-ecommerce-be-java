package com.pk.core.business.storage;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Parses the full XML without DTD/entities and accepts only passive, self-contained SVG. */
final class SvgUploadValidator {
    private SvgUploadValidator() {}

    static void validate(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            XMLStreamReader reader = readerFactory().createXMLStreamReader(input);
            try {
                validateDocument(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IOException failure) {
            throw invalid();
        }
    }

    private static XMLInputFactory readerFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External resources are forbidden");
        });
        return factory;
    }

    private static void validateDocument(XMLStreamReader reader) throws XMLStreamException {
        int depth = 0;
        int elements = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE
                    || event == XMLStreamConstants.PROCESSING_INSTRUCTION) throw invalid();
            if (event == XMLStreamConstants.END_ELEMENT) depth--;
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            if (++depth > SvgUploadPolicy.MAX_DEPTH || ++elements > SvgUploadPolicy.MAX_ELEMENTS) throw invalid();
            if (elements == 1 && !"svg".equals(reader.getLocalName())) throw invalid();
            validateElement(reader);
        }
        if (elements == 0) throw invalid();
    }

    private static void validateElement(XMLStreamReader reader) {
        if (!SvgUploadPolicy.NAMESPACE.equals(reader.getNamespaceURI())
                || !SvgUploadPolicy.ELEMENTS.contains(reader.getLocalName())) throw invalid();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String namespace = reader.getAttributeNamespace(index);
            if (namespace != null && !namespace.isEmpty()
                    && !SvgUploadPolicy.XLINK_NAMESPACE.equals(namespace)
                    && !"http://www.w3.org/XML/1998/namespace".equals(namespace)) throw invalid();
            validateAttribute(reader.getAttributeLocalName(index), reader.getAttributeValue(index));
        }
    }

    private static void validateAttribute(String name, String value) {
        if (!SvgUploadPolicy.ATTRIBUTES.contains(name)) throw invalid();
        if ("href".equals(name) && !SvgUploadPolicy.FRAGMENT.matcher(value.trim()).matches()) throw invalid();
        String withoutLocalUrls = SvgUploadPolicy.LOCAL_URL.matcher(value).replaceAll("");
        String normalized = withoutLocalUrls.toLowerCase(Locale.ROOT);
        if (SvgUploadPolicy.URL_FUNCTION.matcher(normalized).find() || normalized.contains("\\")
                || normalized.contains("javascript:") || normalized.contains("data:")
                || normalized.contains("expression(")) throw invalid();
        if ("style".equals(name)) validateStyle(value);
    }

    private static void validateStyle(String style) {
        if (!SvgUploadPolicy.STYLE_VALUE.matcher(style).matches()) throw invalid();
        for (String declaration : style.split(";")) {
            if (declaration.isBlank()) continue;
            int separator = declaration.indexOf(':');
            if (separator < 1 || !SvgUploadPolicy.STYLE_PROPERTIES.contains(
                    declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT))) throw invalid();
        }
    }

    private static BusinessException invalid() {
        return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                "SVG không hợp lệ hoặc chứa script, nội dung nhúng hay liên kết ngoài. Dùng SVG tĩnh hoặc PNG.");
    }
}
