package org.meldtech.platform.util;

import java.util.Base64;

public class MessageEncoding {
    public static String base64Decoding(String encodedString) {
        if(encodedString == null || encodedString.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(encodedString));
    }
}
