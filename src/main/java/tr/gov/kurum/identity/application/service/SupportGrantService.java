package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.identity.application.port.in.AccountSummaryView;
import tr.gov.kurum.identity.application.port.in.SupportGrantExtensionView;
import tr.gov.kurum.identity.application.port.in.SupportGrantUseCase;
import tr.gov.kurum.identity.application.port.in.SupportGrantView;
import tr.gov.kurum.identity.application.port.out.SupportGrantRepository;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.domain.model.AccountStatus;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.SupportEndReason;
import tr.gov.kurum.identity.domain.model.SupportGrant;
import tr.gov.kurum.identity.domain.model.SupportGrantExtension;
import tr.gov.kurum.identity.domain.model.SupportGrantExtensionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;
import tr.gov.kurum.identity.domain.model.SupportGrantStatus;
import tr.gov.kurum.identity.domain.model.UserAccount;

@Service
public class SupportGrantService implements SupportGrantUseCase {

    private final SupportGrantRepository grants;
    private final UserAccountRepository accountRepository;
    private final AccountLoader accounts;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    SupportGrantService(SupportGrantRepository grants, UserAccountRepository accountRepository,
                        AccountLoader accounts, TransactionTemplate transactionTemplate, Clock clock) {
        this.grants = grants;
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public SupportGrantView request(AccountId agentId, AccountId customerId, Set<String> scopes) {
        Instant now = clock.instant();
        return inTransaction(() -> {
            requireActive(accounts.load(agentId, AccountType.SUPPORT));
            requireActive(accounts.load(customerId, AccountType.CUSTOMER));
            // Persist expiry of stale grants first: only stored statuses count as "live".
            grants.findByCustomer(customerId).forEach(grant -> expireAndSave(grant, now));
            if (grants.hasLiveGrantForCustomer(customerId)) {
                throw new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_CONFLICT, customerId.value());
            }
            SupportGrant grant = SupportGrant.request(grants.nextId(), agentId, customerId, scopes, now);
            grants.save(grant);
            return toView(grant);
        });
    }

    @Override
    public SupportGrantView approve(AccountId customerId, SupportGrantId grantId) {
        Instant now = clock.instant();
        return mutate(grantId, isCustomer(customerId), grant -> grant.approve(now), now);
    }

    @Override
    public SupportGrantView deny(AccountId customerId, SupportGrantId grantId) {
        Instant now = clock.instant();
        return mutate(grantId, isCustomer(customerId), grant -> grant.deny(now), now);
    }

    @Override
    public SupportGrantView requestExtension(AccountId agentId, SupportGrantId grantId) {
        Instant now = clock.instant();
        return mutate(grantId, isAgent(agentId), grant -> grant.requestExtension(grants::nextExtensionId, now), now);
    }

    @Override
    public SupportGrantView approveExtension(AccountId customerId, SupportGrantId grantId,
                                             SupportGrantExtensionId extensionId) {
        Instant now = clock.instant();
        return mutate(grantId, isCustomer(customerId), grant -> grant.approveExtension(extensionId, now), now);
    }

    @Override
    public SupportGrantView denyExtension(AccountId customerId, SupportGrantId grantId,
                                          SupportGrantExtensionId extensionId) {
        Instant now = clock.instant();
        return mutate(grantId, isCustomer(customerId), grant -> grant.denyExtension(extensionId, now), now);
    }

    @Override
    public SupportGrantView end(AccountId callerId, AccountType callerType, SupportGrantId grantId) {
        Instant now = clock.instant();
        return switch (callerType) {
            case CUSTOMER -> mutate(grantId, isCustomer(callerId),
                grant -> grant.end(SupportEndReason.CUSTOMER_ENDED, now), now);
            case SUPPORT -> mutate(grantId, isAgent(callerId),
                grant -> grant.end(SupportEndReason.AGENT_ENDED, now), now);
            case ADMIN -> throw notFound(grantId);
        };
    }

    @Override
    public List<SupportGrantView> mine(AccountId agentId) {
        Instant now = clock.instant();
        return inTransaction(() -> toViews(expireAll(grants.findByAgent(agentId), now)));
    }

    @Override
    public List<SupportGrantView> incoming(AccountId customerId) {
        Instant now = clock.instant();
        return inTransaction(() -> toViews(expireAll(grants.findByCustomer(customerId), now).stream()
            .filter(grant -> grant.status() == SupportGrantStatus.PENDING
                || grant.status() == SupportGrantStatus.PENDING_EXTENSION)
            .toList()));
    }

    @Override
    public List<SupportGrantView> history(AccountId customerId) {
        Instant now = clock.instant();
        return inTransaction(() -> toViews(expireAll(grants.findByCustomer(customerId), now)));
    }

    @Override
    public SupportGrantView detail(AccountId callerId, AccountType callerType, SupportGrantId grantId) {
        Instant now = clock.instant();
        Predicate<SupportGrant> allowed = switch (callerType) {
            case CUSTOMER -> isCustomer(callerId);
            case SUPPORT -> isAgent(callerId);
            case ADMIN -> grant -> false;
        };
        return inTransaction(() -> {
            SupportGrant grant = load(grantId, allowed);
            expireAndSave(grant, now);
            return toView(grant);
        });
    }

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * Loads the grant (not found for anyone but its parties), expires it when due, applies the change and
     * saves. When a grant has just expired, the change is refused by the domain; the expiry is still stored
     * and the refusal is thrown after the transaction committed.
     */
    private SupportGrantView mutate(SupportGrantId grantId, Predicate<SupportGrant> allowed,
                                    Consumer<SupportGrant> change, Instant now) {
        Outcome outcome = inTransaction(() -> {
            SupportGrant grant = load(grantId, allowed);
            boolean expired = grant.expireIfNeeded(now);
            try {
                change.accept(grant);
            } catch (BusinessRuleException refused) {
                if (!expired) {
                    throw refused;
                }
                grants.save(grant);
                return new Outcome(null, refused);
            }
            grants.save(grant);
            return new Outcome(toView(grant), null);
        });
        if (outcome.refusal() != null) {
            throw outcome.refusal();
        }
        return outcome.view();
    }

    private SupportGrant load(SupportGrantId grantId, Predicate<SupportGrant> allowed) {
        return grants.findById(grantId).filter(allowed).orElseThrow(() -> notFound(grantId));
    }

    private static Predicate<SupportGrant> isCustomer(AccountId customerId) {
        return grant -> grant.customerId().equals(customerId);
    }

    private static Predicate<SupportGrant> isAgent(AccountId agentId) {
        return grant -> grant.agentId().equals(agentId);
    }

    private static EntityNotFoundException notFound(SupportGrantId grantId) {
        return new EntityNotFoundException(IdentityErrorKey.SUPPORT_GRANT_NOT_FOUND, grantId.value());
    }

    /** A disabled account is reported like a missing one. */
    private static void requireActive(UserAccount account) {
        if (account.status() != AccountStatus.ACTIVE) {
            throw new EntityNotFoundException(IdentityErrorKey.ACCOUNT_NOT_FOUND, account.id().value());
        }
    }

    private void expireAndSave(SupportGrant grant, Instant now) {
        if (grant.expireIfNeeded(now)) {
            grants.save(grant);
        }
    }

    private List<SupportGrant> expireAll(List<SupportGrant> list, Instant now) {
        list.forEach(grant -> expireAndSave(grant, now));
        return list;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> work.get()));
    }

    private List<SupportGrantView> toViews(List<SupportGrant> list) {
        Map<Long, String> emails = emailsOf(list);
        return list.stream().map(grant -> toView(grant, emails)).toList();
    }

    private SupportGrantView toView(SupportGrant grant) {
        return toView(grant, emailsOf(List.of(grant)));
    }

    /** One lookup for all parties of all grants: no query per row. */
    private Map<Long, String> emailsOf(List<SupportGrant> list) {
        Set<AccountId> ids = new HashSet<>();
        for (SupportGrant grant : list) {
            ids.add(grant.agentId());
            ids.add(grant.customerId());
        }
        return accountRepository.findSummaries(ids).stream()
            .collect(Collectors.toMap(AccountSummaryView::id, AccountSummaryView::email));
    }

    private static SupportGrantView toView(SupportGrant grant, Map<Long, String> emails) {
        return new SupportGrantView(
            grant.id().value(),
            grant.agentId().value(),
            grant.customerId().value(),
            emails.get(grant.agentId().value()),
            emails.get(grant.customerId().value()),
            grant.status().name(),
            grant.scopes().stream().sorted(Comparator.naturalOrder()).toList(),
            grant.initialMinutes(),
            grant.extensionCount(),
            grant.totalApprovedExtensionMinutes(),
            grant.expiresAt(),
            grant.requestedAt(),
            grant.startedAt(),
            grant.endedAt(),
            grant.endReason() == null ? null : grant.endReason().name(),
            grant.extensions().stream().map(SupportGrantService::toExtensionView).toList());
    }

    private static SupportGrantExtensionView toExtensionView(SupportGrantExtension extension) {
        return new SupportGrantExtensionView(
            extension.id().value(),
            extension.requestedMinutes(),
            extension.requestedAt(),
            extension.decision().name(),
            extension.decidedAt());
    }

    private record Outcome(SupportGrantView view, BusinessRuleException refusal) {
    }
}
