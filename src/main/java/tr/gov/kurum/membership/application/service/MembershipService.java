package tr.gov.kurum.membership.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.membership.application.port.in.GrantMembershipCommand;
import tr.gov.kurum.membership.application.port.in.MembershipAdministrationUseCase;
import tr.gov.kurum.membership.application.port.in.MembershipQueryUseCase;
import tr.gov.kurum.membership.application.port.in.MembershipView;
import tr.gov.kurum.membership.application.port.out.CustomerRegistry;
import tr.gov.kurum.membership.application.port.out.MembershipRepository;
import tr.gov.kurum.membership.domain.model.Membership;
import tr.gov.kurum.membership.domain.model.MembershipErrorKey;
import tr.gov.kurum.membership.domain.model.ValidityPeriod;

@Service
public class MembershipService implements MembershipQueryUseCase, MembershipAdministrationUseCase {

    private final MembershipRepository repository;
    private final CustomerRegistry customers;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    MembershipService(MembershipRepository repository, CustomerRegistry customers,
                      TransactionTemplate transactionTemplate, Clock clock) {
        this.repository = repository;
        this.customers = customers;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<MembershipView> membershipOf(AccountId member) {
        Instant now = clock.instant();
        return repository.findById(member).map(membership -> MembershipView.of(membership, now));
    }

    @Override
    public List<MembershipView> membershipsOf(Set<AccountId> members) {
        if (members.size() > MAX_LOOKUP_IDS) {
            throw new IllegalArgumentException("at most " + MAX_LOOKUP_IDS + " ids per lookup");
        }
        Instant now = clock.instant();
        return repository.findAllById(members).stream().map(membership -> MembershipView.of(membership, now)).toList();
    }

    @Override
    public Optional<String> currentTierName(AccountId member) {
        Instant now = clock.instant();
        return repository.findById(member).flatMap(membership -> membership.tierValidAt(now)).map(Enum::name);
    }

    @Override
    public void grant(GrantMembershipCommand command) {
        if (!customers.exists(command.memberId())) {
            throw new EntityNotFoundException(MembershipErrorKey.MEMBER_NOT_FOUND, command.memberId().value());
        }
        ValidityPeriod period = new ValidityPeriod(clock.instant(), command.validUntil());
        transactionTemplate.executeWithoutResult(status -> {
            Membership membership = repository.findById(command.memberId())
                .map(existing -> {
                    existing.regrant(command.tier(), period);
                    return existing;
                })
                .orElseGet(() -> Membership.grant(command.memberId(), command.tier(), period));
            repository.save(membership);
        });
    }

    @Override
    public void cancel(AccountId member) {
        transactionTemplate.executeWithoutResult(status -> repository.delete(repository.findById(member)
            .orElseThrow(() -> new EntityNotFoundException(MembershipErrorKey.MEMBERSHIP_NOT_FOUND, member.value()))));
    }
}
