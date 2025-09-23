package org.meldtech.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("app_registered_client")
public record AppClientConfig(@Id
                              Long id,
                              String applicationId,
                              String appLoginUrl,
                              String appLogoutUrl,
                              String appResolvedPathUrl) {
}
