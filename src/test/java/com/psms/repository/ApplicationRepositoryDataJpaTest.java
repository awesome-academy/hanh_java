package com.psms.repository;

import com.psms.entity.Application;
import com.psms.entity.Citizen;
import com.psms.entity.Department;
import com.psms.entity.ServiceCategory;
import com.psms.entity.ServiceType;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class ApplicationRepositoryDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationRepository applicationRepository;

    private Citizen citizenA;
    private Citizen citizenB;
    private ServiceType serviceType;
    private Application appSubmittedByCitizenA;
    private Application appProcessingByCitizenA;
    private Application appApprovedByCitizenB;

    @BeforeEach
    void setUp() {
        Department department = persistDepartment();
        ServiceCategory category = persistCategory();
        serviceType = persistServiceType(category, department);

        // Mỗi Citizen cần 1 User tương ứng (quan hệ 1-1)
        User userA = persistUser("citizen.a@example.com", "Citizen A");
        User userB = persistUser("citizen.b@example.com", "Citizen B");
        citizenA = persistCitizen(userA, "001234567890");
        citizenB = persistCitizen(userB, "001234567891");

        appSubmittedByCitizenA = persistApplication(
                "HS-20260331-00001",
                citizenA,
                ApplicationStatus.SUBMITTED,
                LocalDateTime.of(2026, 3, 10, 9, 0)
        );
        appProcessingByCitizenA = persistApplication(
                "HS-20260331-00002",
                citizenA,
                ApplicationStatus.PROCESSING,
                LocalDateTime.of(2026, 3, 15, 14, 30)
        );
        appApprovedByCitizenB = persistApplication(
                "HS-20260331-00003",
                citizenB,
                ApplicationStatus.APPROVED,
                LocalDateTime.of(2026, 3, 20, 8, 15)
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void filtersByStatus() {
        List<Application> results = applicationRepository.findAll(
                ApplicationSpecifications.withFilters(ApplicationStatus.PROCESSING, null, null, null),
                Sort.by(Sort.Direction.ASC, "submittedAt")
        );

        assertEquals(1, results.size());
        assertEquals(appProcessingByCitizenA.getApplicationCode(), results.getFirst().getApplicationCode());
    }

    @Test
    void filtersByCitizen() {
        List<Application> results = applicationRepository.findAll(
                ApplicationSpecifications.withFilters(null, citizenA.getId(), null, null),
                Sort.by(Sort.Direction.ASC, "submittedAt")
        );

        assertEquals(2, results.size());
        assertEquals(List.of(
                appSubmittedByCitizenA.getApplicationCode(),
                appProcessingByCitizenA.getApplicationCode()
        ), results.stream().map(Application::getApplicationCode).toList());
    }

    @Test
    void filtersBySubmittedDateRange() {
        List<Application> results = applicationRepository.findAll(
                ApplicationSpecifications.withFilters(
                        null,
                        null,
                        LocalDateTime.of(2026, 3, 11, 0, 0),
                        LocalDateTime.of(2026, 3, 19, 23, 59)
                ),
                Sort.by(Sort.Direction.ASC, "submittedAt")
        );

        assertEquals(1, results.size());
        assertEquals(appProcessingByCitizenA.getApplicationCode(), results.getFirst().getApplicationCode());
    }

    @Test
    void combinesStatusCitizenAndDateRangeFilters() {
        List<Application> results = applicationRepository.findAll(
                ApplicationSpecifications.withFilters(
                        ApplicationStatus.SUBMITTED,
                        citizenA.getId(),
                        LocalDateTime.of(2026, 3, 1, 0, 0),
                        LocalDateTime.of(2026, 3, 12, 23, 59)
                ),
                Sort.by(Sort.Direction.ASC, "submittedAt")
        );

        assertEquals(1, results.size());
        assertEquals(appSubmittedByCitizenA.getApplicationCode(), results.getFirst().getApplicationCode());
    }

    private Department persistDepartment() {
        Department department = new Department();
        department.setCode("PB-TEST");
        department.setName("Test Department");
        department.setActive(true);
        return entityManager.persistFlushFind(department);
    }

    private ServiceCategory persistCategory() {
        ServiceCategory category = new ServiceCategory();
        category.setCode("TEST_CAT");
        category.setName("Test Category");
        category.setSortOrder((short) 1);
        category.setActive(true);
        return entityManager.persistFlushFind(category);
    }

    private ServiceType persistServiceType(ServiceCategory category, Department department) {
        ServiceType entity = new ServiceType();
        entity.setCode("DV-TEST");
        entity.setName("Test Service");
        entity.setCategory(category);
        entity.setDepartment(department);
        entity.setProcessingTimeDays((short) 5);
        entity.setFee(BigDecimal.ZERO);
        entity.setActive(true);
        return entityManager.persistFlushFind(entity);
    }

    private User persistUser(String email, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setFullName(fullName);
        user.setActive(true);
        user.setLocked(false);
        user.setEmailNotifEnabled(true);
        return entityManager.persistFlushFind(user);
    }

    // Citizen quan hệ 1-1 với User — cần persist riêng sau khi có User
    private Citizen persistCitizen(User user, String nationalId) {
        Citizen citizen = new Citizen();
        citizen.setUser(user);
        citizen.setNationalId(nationalId);
        return entityManager.persistFlushFind(citizen);
    }

    private Application persistApplication(String code,
                                           Citizen citizen,
                                           ApplicationStatus status,
                                           LocalDateTime submittedAt) {
        Application application = new Application();
        application.setApplicationCode(code);
        application.setCitizen(citizen);
        application.setServiceType(serviceType);
        application.setStatus(status);
        application.setSubmittedAt(submittedAt);
        application.setProcessingDeadline(LocalDate.from(submittedAt.plusDays(5)));
        return entityManager.persistFlushFind(application);
    }
}
