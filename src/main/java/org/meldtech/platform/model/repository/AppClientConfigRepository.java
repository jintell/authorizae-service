package org.meldtech.platform.model.repository;

import org.meldtech.platform.model.AppClientConfig;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AppClientConfigRepository extends CrudRepository<AppClientConfig, String> {
    Optional<AppClientConfig> findByApplicationId(String applicationId);
}
