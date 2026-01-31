package com.reajason.noone.server.profile.config;

public final class HttpProtocolConfigDefaults {
    static final String PAYLOAD_PLACEHOLDER = "{{payload}}";
    static final String BOUNDARY_PLACEHOLDER = "{{boundary}}";

    private HttpProtocolConfigDefaults() {
    }

    public static void applyDefaults(HttpProtocolConfig config) {
        if (config == null) {
            return;
        }

        if (config.getRequestBodyType() == null) {
            config.setRequestBodyType(HttpRequestBodyType.FORM_URLENCODED);
        }
        if (config.getRequestTemplate() == null || config.getRequestTemplate().isBlank()) {
            config.setRequestTemplate(defaultTemplate(config.getRequestBodyType()));
        }

        if (config.getResponseBodyType() == null) {
            config.setResponseBodyType(HttpResponseBodyType.TEXT);
        }
        if (config.getResponseTemplate() == null || config.getResponseTemplate().isBlank()) {
            config.setResponseTemplate(defaultTemplate(config.getResponseBodyType()));
        }
    }

    public static String defaultTemplate(HttpRequestBodyType type) {
        return switch (type) {
            case FORM_URLENCODED -> "username=admin&action=login&q={{payload}}&token=123456";
            case TEXT -> "hello{{payload}}world";
            case MULTIPART_FORM_DATA -> """
                    --{{boundary}}
                    Content-Disposition: form-data; name="username"
                    
                    admin
                    --{{boundary}}
                    Content-Disposition: form-data; name="file"; filename="test.png"
                    Content-Type: image/png
                    
                    <hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
                    --{{boundary}}--
                    """;
            case JSON -> "{\"hello\": \"{{payload}}\"}";
            case XML -> "<hello>{{payload}}</hello>";
            case BINARY -> "<base64>aGVsbG8=</base64>{{payload}}";
        };
    }

    public static String defaultTemplate(HttpResponseBodyType type) {
        return switch (type) {
            case FORM_URLENCODED -> "username=admin&action=login&q={{payload}}&token=123456";
            case TEXT -> "hello{{payload}}world";
            case MULTIPART_FORM_DATA -> """
                    --{{boundary}}
                    Content-Disposition: form-data; name="username"
                    
                    admin
                    --{{boundary}}
                    Content-Disposition: form-data; name="file"; filename="test.png"
                    Content-Type: image/png
                    
                    <hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
                    --{{boundary}}--
                    """;
            case JSON -> "{\"hello\": \"{{payload}}\"}";
            case XML -> "<hello>{{payload}}</hello>";
            case BINARY -> "<base64>aGVsbG8=</base64>{{payload}}";
        };
    }
}
