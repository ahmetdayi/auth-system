package tr.gov.kurum.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.CommonErrorKey;
import tr.gov.kurum.common.domain.ErrorKey;
import tr.gov.kurum.content.domain.model.ContentErrorKey;
import tr.gov.kurum.customer.domain.model.CustomerErrorKey;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.security.SecurityErrorKey;
import tr.gov.kurum.membership.domain.model.MembershipErrorKey;
import tr.gov.kurum.staff.domain.model.StaffErrorKey;

/** A new error key without a message would reach clients as a raw key; this fails the build instead. */
class ErrorMessagesTest {

    @Test
    void every_error_key_has_a_message_and_every_message_a_key() throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/messages.properties")) {
            assertThat(in).isNotNull();
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> expectedKeys = allErrorKeys().stream().map(ErrorKey::messageKey).toList();

        List<String> missing = new ArrayList<>();
        for (String key : expectedKeys) {
            if (messages.getProperty(key, "").isBlank()) {
                missing.add(key);
            }
        }
        List<String> orphaned = messages.stringPropertyNames().stream()
            .filter(key -> !expectedKeys.contains(key))
            .toList();

        assertThat(missing).as("error keys without a message").isEmpty();
        assertThat(orphaned).as("messages without an error key").isEmpty();
    }

    /**
     * Codes are the API contract and all contexts share one message file: two contexts using the same code
     * would make one of the messages wrong for the other.
     */
    @Test
    void error_codes_are_unique_across_bounded_contexts() {
        List<String> codes = allErrorKeys().stream().map(ErrorKey::code).toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    private static List<ErrorKey> allErrorKeys() {
        return Stream.of(CommonErrorKey.values(), SecurityErrorKey.values(), IdentityErrorKey.values(),
                CustomerErrorKey.values(), MembershipErrorKey.values(), StaffErrorKey.values(),
                ContentErrorKey.values())
            .<ErrorKey>flatMap(Stream::of)
            .toList();
    }

    @Test
    void messages_never_contain_a_single_apostrophe() throws IOException {
        // MessageFormat treats ' as a quote and silently drops the rest of the message.
        Properties messages = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/messages.properties")) {
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(messages.values()).noneMatch(value -> value.toString().matches(".*(?<!')'(?!').*"));
    }
}
