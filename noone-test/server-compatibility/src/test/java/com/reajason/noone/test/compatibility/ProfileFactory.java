package com.reajason.noone.test.compatibility;

import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;

import java.util.List;

public class ProfileFactory {
    public static Profile get() {
        Profile profile = new Profile();
        profile.setProtocolType(ProtocolType.HTTP);
        profile.setName("RuoYi（JSON）");
        profile.setPassword("secret");
        IdentifierConfig identifierConfig = new IdentifierConfig();
        identifierConfig.setLocation(IdentifierLocation.HEADER);
        identifierConfig.setOperator(IdentifierOperator.CONTAINS);
        identifierConfig.setName("No-One-Version");
        identifierConfig.setValue("V1");
        profile.setIdentifier(identifierConfig);
        HttpProtocolConfig httpProtocolConfig = new HttpProtocolConfig();
        httpProtocolConfig.setRequestBodyType(HttpRequestBodyType.JSON);
        httpProtocolConfig.setResponseBodyType(HttpResponseBodyType.JSON);
        httpProtocolConfig.setRequestTemplate("{\"signature\": \"{{payload}}\", \"version\": \"v1\"}");
        httpProtocolConfig.setResponseTemplate("{\"resData\": \"{{payload}}\", \"test\": \"123\"}");
        profile.setProtocolConfig(httpProtocolConfig);
        profile.setRequestTransformations(List.of("Gzip", "XOR", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "TripleDES", "Hex"));
        return profile;
    }
}
