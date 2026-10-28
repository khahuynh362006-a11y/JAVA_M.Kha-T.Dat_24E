package com.khadat.cinema.config;

import com.khadat.cinema.service.LocalAccountProvisioningService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("local-accounts")
public class LocalAccountRunner implements ApplicationRunner {
    private final LocalAccountProvisioningService accounts;
    private final Environment env;
    public LocalAccountRunner(LocalAccountProvisioningService accounts, Environment env) { this.accounts=accounts;this.env=env; }
    @Override public void run(ApplicationArguments args) {
        accounts.create(env);
        System.out.println("Local account created. Credentials were not printed. Stop using this profile after provisioning.");
    }
}
