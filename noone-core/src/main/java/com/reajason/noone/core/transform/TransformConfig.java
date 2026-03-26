package com.reajason.noone.core.transform;

import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;

import java.util.List;
import java.util.UUID;

public record TransformConfig(
        String password,
        TransformationSpec requestSpec,
        TransformationSpec responseSpec,
        HttpRequestBodyType requestBodyType,
        String requestTemplate,
        HttpResponseBodyType responseBodyType,
        String responseTemplate,
        String contentType
) {
    public static TransformConfig from(String password,
                                       List<String> requestTransformations,
                                       List<String> responseTransformations) {
        return new TransformConfig(
                password,
                TransformationSpec.parse(requestTransformations),
                TransformationSpec.parse(responseTransformations),
                null, null, null, null, null
        );
    }

    public static TransformConfig from(String password,
                                       List<String> requestTransformations,
                                       List<String> responseTransformations,
                                       HttpRequestBodyType requestBodyType,
                                       String requestTemplate,
                                       HttpResponseBodyType responseBodyType,
                                       String responseTemplate) {
        return new TransformConfig(
                password,
                TransformationSpec.parse(requestTransformations),
                TransformationSpec.parse(responseTransformations),
                requestBodyType, requestTemplate,
                responseBodyType, responseTemplate,
                resolveContentType(requestBodyType, null)
        );
    }

    public static TransformConfig fromProfile(Profile profile) {
        ProtocolConfig protocolConfig = profile.getProtocolConfig();

        HttpRequestBodyType reqBodyType = null;
        String reqTemplate = null;
        HttpResponseBodyType resBodyType = null;
        String resTemplate = null;
        String contentType = null;

        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            reqBodyType = httpConfig.getRequestBodyType();
            reqTemplate = httpConfig.getRequestTemplate();
            resBodyType = httpConfig.getResponseBodyType();
            resTemplate = httpConfig.getResponseTemplate();

            if (reqBodyType == HttpRequestBodyType.MULTIPART_FORM_DATA) {
                String boundary = "NoOneBoundary" + UUID.randomUUID().toString().replace("-", "");
                if (reqTemplate != null) {
                    reqTemplate = reqTemplate.replace("{{boundary}}", boundary);
                }
                contentType = "multipart/form-data; boundary=" + boundary;
            } else {
                contentType = resolveContentType(reqBodyType, null);
            }
        } else if (protocolConfig instanceof WebSocketProtocolConfig wsConfig) {
            reqBodyType = HttpRequestBodyType.BINARY;
            reqTemplate = wsConfig.getMessageTemplate();
            resBodyType = HttpResponseBodyType.BINARY;
            resTemplate = wsConfig.getResponseTemplate();
        } else if (protocolConfig instanceof DubboProtocolConfig dubboConfig) {
            if (dubboConfig.getRequestTemplate() != null && !dubboConfig.getRequestTemplate().isEmpty()) {
                reqBodyType = HttpRequestBodyType.BINARY;
                reqTemplate = dubboConfig.getRequestTemplate();
            }
            if (dubboConfig.getResponseTemplate() != null && !dubboConfig.getResponseTemplate().isEmpty()) {
                resBodyType = HttpResponseBodyType.BINARY;
                resTemplate = dubboConfig.getResponseTemplate();
            }
        }

        return new TransformConfig(
                profile.getPassword(),
                TransformationSpec.parse(profile.getRequestTransformations()),
                TransformationSpec.parse(profile.getResponseTransformations()),
                reqBodyType, reqTemplate, resBodyType, resTemplate, contentType
        );
    }

    private static String resolveContentType(HttpRequestBodyType bodyType, String explicitContentType) {
        if (explicitContentType != null) {
            return explicitContentType;
        }
        if (bodyType == null) {
            return "application/x-www-form-urlencoded; charset=utf-8";
        }
        return switch (bodyType) {
            case FORM_URLENCODED -> "application/x-www-form-urlencoded; charset=utf-8";
            case JSON -> "application/json; charset=utf-8";
            case XML -> "application/xml; charset=utf-8";
            case TEXT -> "text/plain; charset=utf-8";
            case BINARY, MULTIPART_FORM_DATA -> "application/octet-stream";
        };
    }
}
