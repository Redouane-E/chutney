package com.chutneytesting.scenario.infra.raw;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Long.valueOf;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import com.chutneytesting.campaign.infra.CampaignScenarioJpaRepository;
import com.chutneytesting.campaign.infra.jpa.CampaignScenario;
import com.chutneytesting.execution.infra.storage.DatabaseExecutionJpaRepository;
import com.chutneytesting.execution.infra.storage.jpa.ScenarioExecution;
import com.chutneytesting.scenario.domain.gwt.GwtTestCase;
import com.chutneytesting.scenario.infra.jpa.Scenario;
import com.chutneytesting.server.core.domain.scenario.AggregatedRepository;
import com.chutneytesting.server.core.domain.scenario.ScenarioNotFoundException;
import com.chutneytesting.server.core.domain.scenario.ScenarioNotParsableException;
import com.chutneytesting.server.core.domain.scenario.TestCase;
import com.chutneytesting.server.core.domain.scenario.TestCaseMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class DatabaseTestCaseRepository implements AggregatedRepository<GwtTestCase> {

    private final ScenarioJpaRepository scenarioJpaRepository;
    private final DatabaseExecutionJpaRepository scenarioExecutionsJpaRepository;
    private final CampaignScenarioJpaRepository campaignScenarioJpaRepository;
    private final EntityManager entityManager;
    private final Pattern pattern = Pattern.compile("\"([^\"]*)\"");


    public DatabaseTestCaseRepository(
        ScenarioJpaRepository jpa,
        DatabaseExecutionJpaRepository scenarioExecutionsJpaRepository,
        CampaignScenarioJpaRepository campaignScenarioJpaRepository, EntityManager entityManager
    ) {
        this.scenarioJpaRepository = jpa;
        this.scenarioExecutionsJpaRepository = scenarioExecutionsJpaRepository;
        this.campaignScenarioJpaRepository = campaignScenarioJpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public String save(GwtTestCase testCase) {
        if (testCaseDoesNotExist(testCase.id())) {
          saveScenarioWithExplicitId(testCase);
          return testCase.id();
        }
        try {
            return scenarioJpaRepository.save(Scenario.fromGwtTestCase(testCase)).getId().toString();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ScenarioNotFoundException(testCase.id(), testCase.metadata().version());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GwtTestCase> findById(String scenarioId) {
        if (checkIdInput(scenarioId)) {
            return empty();
        }
        Optional<Scenario> scenarioDao = scenarioJpaRepository.findByIdAndActivated(valueOf(scenarioId), true)
            .filter(Scenario::isActivated);
        return scenarioDao.map(Scenario::toGwtTestCase);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestCase> findExecutableById(String id) {
        return findById(id).map(TestCase.class::cast);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestCaseMetadata> findMetadataById(String testCaseId) {
        if (checkIdInput(testCaseId)) {
            return empty();
        }
        return scenarioJpaRepository.findMetaDataByIdAndActivated(valueOf(testCaseId), true).map(Scenario::toTestCaseMetadata);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestCaseMetadata> findAll() {
        return scenarioJpaRepository.findMetaDataByActivatedTrue().stream()
            .map(Scenario::toTestCaseMetadata)
            .toList();
    }

    @Override
    public void removeById(String scenarioId) {
        if (checkIdInput(scenarioId)) {
            return;
        }
        scenarioJpaRepository.findByIdAndActivated(valueOf(scenarioId), true)
            .ifPresent(scenarioJpa -> {
                List<ScenarioExecution> allExecutions = scenarioExecutionsJpaRepository.findAllByScenarioId(scenarioId);
                allExecutions.forEach(e -> {
                    e.forCampaignExecution(null);
                    scenarioExecutionsJpaRepository.save(e);
                });

                List<CampaignScenario> allCampaignScenarios = campaignScenarioJpaRepository.findAllByScenarioId(scenarioId);
                campaignScenarioJpaRepository.deleteAll(allCampaignScenarios);

                scenarioJpa.deactivate();
                scenarioJpaRepository.save(scenarioJpa);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> lastVersion(String scenarioId) {
        if (checkIdInput(scenarioId)) {
            return empty();
        }
        try {
            return scenarioJpaRepository.lastVersion(valueOf(scenarioId));
        } catch (IncorrectResultSizeDataAccessException e) {
            return empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestCaseMetadata> search(String textFilter) {
        if (!textFilter.isEmpty()) {
            List<String> words = getWordsToSearchWithQuotes(escapeSql(textFilter));
            Specification<Scenario> scenarioDaoSpecification = buildLikeSpecificationOnContent(words);

            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Scenario> query = builder.createQuery(Scenario.class);
            Root<Scenario> root = query.from(Scenario.class);
            query.select(builder.construct(Scenario.class, root.get("id"), root.get("title"), root.get("description"), root.get("tags"), root.get("creationDate"), root.get("dataset"), root.get("activated"), root.get("userId"), root.get("updateDate"), root.get("version"), root.get("defaultDataset")));
            query = query.where(scenarioDaoSpecification.toPredicate(root, query, builder));

            return entityManager.createQuery(query).getResultList().stream().map(Scenario::toTestCaseMetadata).toList();
        } else {
            return findAll();
        }
    }

    private boolean testCaseDoesNotExist(String id) {
        try {
            return Long.parseLong(id) >= 0 && findById(id).isEmpty();
        } catch (NumberFormatException e) {
            throw new ScenarioNotParsableException("Cannot parse id", e);
        }
    }

    List<String> getWordsToSearchWithQuotes(String input) {
        List<String> words = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            String word = matcher.group(1);
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        words.addAll(Arrays.stream(input.replaceAll(pattern.pattern(), "").split("\\s")).filter(value -> !value.isEmpty()).toList());
        return words;
    }

    private void saveScenarioWithExplicitId(GwtTestCase testCase) {
        Scenario scenario = Scenario.fromGwtTestCase(testCase);
        scenarioJpaRepository.saveWithExplicitId(
            scenario.getId(),
            scenario.getTitle(),
            scenario.getDescription(),
            scenario.getContent(),
            scenario.getTags(),
            scenario.getCreationDate(),
            scenario.getDataset(),
            scenario.isActivated(),
            scenario.getUserId(),
            scenario.getUpdateDate(),
            scenario.getVersion(),
            scenario.getDefaultDataset()
        );
    }

    private static String escapeSql(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("'", "''");
    }

    private Specification<Scenario> buildLikeSpecificationOnContent(List<String> words) {
        Specification<Scenario> scenarioDaoSpecification = null;
        for (String word : words) {
            Specification<Scenario> wordSpecification = ScenarioJpaRepository.contentContains(word);
            scenarioDaoSpecification = ofNullable(scenarioDaoSpecification)
                .map(s -> s.or(wordSpecification))
                .orElse(wordSpecification);
        }
        return scenarioDaoSpecification;
    }

    private boolean checkIdInput(String scenarioId) {
        return isNullOrEmpty(scenarioId) || !isNumeric(scenarioId);
    }
}
