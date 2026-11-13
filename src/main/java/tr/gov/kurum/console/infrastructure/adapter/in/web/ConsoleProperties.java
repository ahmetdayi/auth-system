package tr.gov.kurum.console.infrastructure.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param showDemoAccounts lists the demo accounts and their password on the home page; local profile only */
@ConfigurationProperties(prefix = "app.console")
public record ConsoleProperties(boolean showDemoAccounts) {
}
