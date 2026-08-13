package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.zuperman.support_trainer.availability.dto.request.CreateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.DeactivateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityMaterializationService;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.availability.service.WeeklyAvailabilityRuleService;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.AuthenticatedUserPrincipal;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AvailabilityMaterializationConcurrencyIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");

    @Autowired
    private AvailabilityMaterializationService materializationService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private WeeklyAvailabilityRuleService weeklyRuleService;

    @Autowired
    private WeeklyAvailabilityRuleRepository weeklyRuleRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldMaterializeTheSameRuleConcurrentlyWithoutDuplicatesOrErrors() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);
        availabilitySlotRepository.deleteAll(ruleSlots(rule.getId()));
        availabilitySlotRepository.flush();

        runConcurrently(
                () -> materializationService.synchronizeRule(rule.getId(), professional.getId()),
                () -> materializationService.synchronizeRule(rule.getId(), professional.getId())
        );

        List<AvailabilitySlot> materialized = ruleSlots(rule.getId());
        assertThat(materialized).isNotEmpty();
        assertThat(materialized).extracting(AvailabilitySlot::getStartDateTime).doesNotHaveDuplicates();
    }

    @Test
    void shouldNotRecreateStaleWindowsWhenMaterializationRacesWithUpdate() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);

        runConcurrently(
                () -> materializationService.synchronizeRule(rule.getId(), professional.getId()),
                () -> {
                    authenticateAs(professional);
                    weeklyRuleService.update(rule.getId(), new UpdateWeeklyAvailabilityRuleRequest(
                            rule.getDayOfWeek(),
                            LocalTime.of(14, 0),
                            LocalTime.of(16, 0),
                            List.of(45, 60, 120),
                            "Studio nuovo",
                            2,
                            null
                    ));
                }
        );

        List<AvailabilitySlot> active = ruleSlots(rule.getId()).stream()
                .filter(slot -> Boolean.TRUE.equals(slot.getActive()))
                .toList();
        assertThat(active).isNotEmpty().allSatisfy(slot -> {
            assertThat(slot.getStartDateTime().atZone(BUSINESS_ZONE).toLocalTime())
                    .isEqualTo(LocalTime.of(14, 0));
            assertThat(slot.getEndDateTime().atZone(BUSINESS_ZONE).toLocalTime())
                    .isEqualTo(LocalTime.of(16, 0));
            assertThat(slot.getLocationLabel()).isEqualTo("Studio nuovo");
        });
    }

    @Test
    void shouldNotRecreateWindowsWhenMaterializationRacesWithDeactivate() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);

        runConcurrently(
                () -> materializationService.synchronizeRule(rule.getId(), professional.getId()),
                () -> {
                    authenticateAs(professional);
                    weeklyRuleService.deactivate(
                            rule.getId(),
                            new DeactivateWeeklyAvailabilityRuleRequest(null)
                    );
                }
        );

        assertThat(weeklyRuleRepository.findById(rule.getId()).orElseThrow().getActive()).isFalse();
        assertThat(ruleSlots(rule.getId()))
                .allSatisfy(slot -> assertThat(slot.getActive()).isFalse());
    }

    @Test
    void shouldRefreshManagedPreloadAfterConcurrentUpdateCommit() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            WeeklyAvailabilityRule stale = weeklyRuleRepository.findById(rule.getId()).orElseThrow();
            assertThat(stale.getStartTime()).isEqualTo(LocalTime.of(9, 0));

            runCommittedMutation(() -> {
                authenticateAs(professional);
                weeklyRuleService.update(rule.getId(), new UpdateWeeklyAvailabilityRuleRequest(
                        rule.getDayOfWeek(),
                        LocalTime.of(14, 0),
                        LocalTime.of(16, 0),
                        List.of(45, 60, 120),
                        "Studio fresh",
                        2,
                        null
                ));
            });

            materializationService.synchronizeRule(rule.getId(), professional.getId());
            assertThat(stale.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        });

        assertOnlyCurrentConfigurationIsActive(rule.getId(), LocalTime.of(14, 0), "Studio fresh");
    }

    @Test
    void shouldNotMaterializeManagedPreloadAfterConcurrentDeactivateCommit() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            WeeklyAvailabilityRule stale = weeklyRuleRepository.findById(rule.getId()).orElseThrow();
            assertThat(stale.getActive()).isTrue();

            runCommittedMutation(() -> {
                authenticateAs(professional);
                weeklyRuleService.deactivate(
                        rule.getId(),
                        new DeactivateWeeklyAvailabilityRuleRequest(null)
                );
            });
            materializationService.synchronizeRule(rule.getId(), professional.getId());
        });

        assertThat(weeklyRuleRepository.findById(rule.getId()).orElseThrow().getActive()).isFalse();
        assertThat(ruleSlots(rule.getId()))
                .allSatisfy(slot -> assertThat(slot.getActive()).isFalse());
    }

    @Test
    void shouldRefreshListMinePreloadBeforeOnDemandSynchronization() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            authenticateAs(professional);
            assertThat(weeklyRuleService.listMine()).singleElement()
                    .satisfies(response -> assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0)));
            runCommittedMutation(() -> {
                authenticateAs(professional);
                weeklyRuleService.update(rule.getId(), new UpdateWeeklyAvailabilityRuleRequest(
                        rule.getDayOfWeek(),
                        LocalTime.of(15, 0),
                        LocalTime.of(17, 0),
                        List.of(45, 60, 120),
                        "Studio on demand",
                        2,
                        null
                ));
            });
            materializationService.synchronizeProfessional(professional.getId());
        });

        assertOnlyCurrentConfigurationIsActive(rule.getId(), LocalTime.of(15, 0), "Studio on demand");
    }

    @Test
    void shouldIgnoreSchedulerCandidateCapturedBeforeDeactivate() throws Exception {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);
        LocalDate horizonEnd = LocalDate.now(BUSINESS_ZONE).plusMonths(6);
        var candidate = weeklyRuleRepository.findMaterializationCandidates(horizonEnd).stream()
                .filter(item -> item.getRuleId().equals(rule.getId()))
                .findFirst()
                .orElseThrow();

        authenticateAs(professional);
        weeklyRuleService.deactivate(rule.getId(), new DeactivateWeeklyAvailabilityRuleRequest(null));
        materializationService.synchronizeRule(candidate.getRuleId(), candidate.getProfessionalId());
        materializationService.synchronizeRollingHorizon();

        assertThat(ruleSlots(rule.getId()))
                .allSatisfy(slot -> assertThat(slot.getActive()).isFalse());
    }

    @Test
    void shouldSynchronizeOnlyTheOccurrenceReadDuringInitialProfessionalLoad() {
        ProfessionalProfile professional = createProfessional();
        WeeklyAvailabilityRule rule = createRule(professional);
        availabilitySlotRepository.deleteAll(ruleSlots(rule.getId()));
        availabilitySlotRepository.flush();
        authenticateAs(professional);

        assertThat(weeklyRuleService.listMine()).singleElement();
        assertThat(ruleSlots(rule.getId())).isEmpty();

        assertThat(availabilityService.getMyAvailabilitySlots()).isNotEmpty();
        assertThat(ruleSlots(rule.getId())).isNotEmpty();
    }

    private WeeklyAvailabilityRule createRule(ProfessionalProfile professional) {
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(7);
        Long id = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(45, 60, 120),
                "Studio",
                2,
                validFrom
        )).id();
        return weeklyRuleRepository.findById(id).orElseThrow();
    }

    private List<AvailabilitySlot> ruleSlots(Long ruleId) {
        return availabilitySlotRepository.findAll().stream()
                .filter(slot -> slot.getWeeklyRule() != null)
                .filter(slot -> slot.getWeeklyRule().getId().equals(ruleId))
                .toList();
    }

    private void runConcurrently(Runnable firstTask, Runnable secondTask) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runAfterLatch(firstTask, ready, start));
            Future<?> second = executor.submit(() -> runAfterLatch(secondTask, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
    }

    private void runAfterLatch(Runnable task, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La contesa non è partita entro il timeout");
            }
            task.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void runCommittedMutation(Runnable mutation) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(mutation).get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertOnlyCurrentConfigurationIsActive(
            Long ruleId,
            LocalTime startTime,
            String location
    ) {
        assertThat(ruleSlots(ruleId).stream().filter(slot -> Boolean.TRUE.equals(slot.getActive())))
                .isNotEmpty()
                .allSatisfy(slot -> {
                    assertThat(slot.getStartDateTime().atZone(BUSINESS_ZONE).toLocalTime())
                            .isEqualTo(startTime);
                    assertThat(slot.getLocationLabel()).isEqualTo(location);
                });
    }

    private ProfessionalProfile createProfessional() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                "pro-" + UUID.randomUUID() + "@test.com",
                "password123",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);
        return professionalProfileRepository.saveAndFlush(professional);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUserPrincipal(user.getId(), user.getEmail()),
                        null,
                        List.of(new SimpleGrantedAuthority(user.getRole().name()))
                )
        );
    }
}
