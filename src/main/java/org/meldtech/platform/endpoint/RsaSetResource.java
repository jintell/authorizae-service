package org.meldtech.platform.endpoint;


import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RsaSetResource {
    private final JWKSet jwkSet;

    @GetMapping("/.well-known/authorization-server/jwks.json")
    public Map<String, Object> keys() {
        return jwkSet.toJSONObject();
    }

}
