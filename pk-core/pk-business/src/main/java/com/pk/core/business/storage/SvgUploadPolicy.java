package com.pk.core.business.storage;

import java.util.Set;
import java.util.regex.Pattern;

/** Passive SVG features allowed for uploaded logos and illustrations. */
final class SvgUploadPolicy {
    static final String NAMESPACE = "http://www.w3.org/2000/svg";
    static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";
    static final int MAX_DEPTH = 128;
    static final int MAX_ELEMENTS = 100000;
    static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "path", "rect", "circle",
            "ellipse", "line", "polyline", "polygon", "text", "tspan", "title", "desc",
            "linearGradient", "radialGradient", "stop", "clipPath", "mask", "use", "symbol");
    static final Set<String> ATTRIBUTES = Set.of("id", "class", "style", "version", "viewBox",
            "width", "height", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry",
            "d", "points", "transform", "fill", "fill-rule", "fill-opacity", "stroke", "stroke-width",
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray",
            "stroke-dashoffset", "stroke-opacity", "opacity", "clip-path", "clip-rule", "mask",
            "maskUnits", "maskContentUnits", "gradientUnits", "gradientTransform", "spreadMethod",
            "fx", "fy", "fr", "offset", "stop-color", "stop-opacity", "preserveAspectRatio",
            "font-family", "font-size", "font-weight", "font-style", "text-anchor",
            "dominant-baseline", "letter-spacing", "dx", "dy", "rotate", "textLength",
            "lengthAdjust", "color", "display", "visibility", "href", "space", "role",
            "aria-label", "aria-hidden", "focusable", "clipPathUnits");
    static final Set<String> STYLE_PROPERTIES = Set.of("fill", "fill-rule", "fill-opacity",
            "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
            "stroke-dasharray", "stroke-dashoffset", "stroke-opacity", "opacity", "color",
            "stop-color", "stop-opacity", "font-family", "font-size", "font-weight", "font-style",
            "text-anchor", "display", "visibility", "clip-path", "clip-rule", "mask");
    static final Pattern FRAGMENT = Pattern.compile("#[A-Za-z_][A-Za-z0-9_.:-]*");
    static final Pattern LOCAL_URL = Pattern.compile("url\\(\\s*#[A-Za-z_][A-Za-z0-9_.:-]*\\s*\\)", Pattern.CASE_INSENSITIVE);
    static final Pattern STYLE_VALUE = Pattern.compile("[A-Za-z0-9\\s#.,:%;()_+\\-]*");
    static final Pattern URL_FUNCTION = Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);

    private SvgUploadPolicy() {}
}
