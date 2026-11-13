package phucuong.ht.modules.training.registrationTemplate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import phucuong.ht.modules.training.registrationTemplate.model.entity.RegistrationExportTemplate;

import java.util.Optional;
import java.util.UUID;

public interface RegistrationExportTemplateRepository extends JpaRepository<RegistrationExportTemplate, UUID> {

    Optional<RegistrationExportTemplate> findByUnitTypeAndUnitId(String unitType, UUID unitId);
}
