package tr.gov.kurum.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.PermissionCodes;

class KnownPermissionTest {

    @Test
    void every_permission_code_constant_appears_in_the_catalog() throws Exception {
        Set<String> catalogCodes = Arrays.stream(KnownPermission.values())
            .map(KnownPermission::code)
            .collect(Collectors.toSet());

        for (Field field : PermissionCodes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String code = (String) field.get(null);
            assertThat(catalogCodes)
                .as("KnownPermission must include PermissionCodes.%s", field.getName())
                .contains(code);
        }
    }

    @Test
    void catalog_entries_have_description_and_group() {
        for (KnownPermission permission : KnownPermission.values()) {
            assertThat(permission.description()).isNotBlank();
            assertThat(permission.group()).matches("^[a-z][a-z0-9-]{0,49}$");
            assertThat(permission.toPermissionName().value()).isEqualTo(permission.code());
        }
    }
}
