package com.prism.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.category.CategoryRepository;
import com.prism.category.EventCategory;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.util.PhoneNumbers;
import com.prism.user.AccountService;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    private final CategoryRepository categories;
    private final TemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final UserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final PrismProperties properties;
    private final ObjectMapper mapper;

    public ReferenceDataSeeder(
            CategoryRepository categories,
            TemplateRepository templates,
            TemplateVersionRepository versions,
            UserRepository users,
            AccountService accounts,
            PasswordEncoder passwordEncoder,
            PrismProperties properties,
            ObjectMapper mapper
    ) {
        this.categories = categories;
        this.templates = templates;
        this.versions = versions;
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean seeded = false;
        for (TemplateCatalog.CategorySeed seed : TemplateCatalog.categories()) {
            if (categories.findByCode(seed.code()).isPresent()) {
                continue;
            }
            EventCategory category = new EventCategory();
            category.name = seed.name();
            category.code = seed.code();
            category.description = seed.description();
            category.thumbnailUrl = seed.thumbnail();
            category.active = true;
            categories.save(category);
            seeded = true;
        }
        for (TemplateCatalog.TemplateSeed seed : TemplateCatalog.templates()) {
            if (templates.findByCode(seed.code()).isPresent()) {
                continue;
            }
            EventCategory category = categories.findByCode(seed.categoryCode()).orElseThrow();
            EventTemplate template = new EventTemplate();
            template.categoryId = category.id;
            template.name = seed.name();
            template.code = seed.code();
            template.description = seed.description();
            template.thumbnailUrl = seed.thumbnail();
            template.active = true;
            templates.save(template);
            TemplateVersion version = new TemplateVersion();
            version.templateId = template.id;
            version.versionNumber = 1;
            version.schemaJson = mapper.valueToTree(seed.schema());
            version.defaultConfiguration = mapper.valueToTree(seed.defaults());
            version.active = true;
            versions.save(version);
            seeded = true;
        }
        if (seeded) {
            log.info("Seeded missing WebAux categories and templates");
        }
        seedAdmin();
    }

    private void seedAdmin() {
        String phone = properties.getAdmin().getInitialPhone();
        String password = properties.getAdmin().getInitialPassword();
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (password.length() < 10) {
            throw new IllegalStateException("PRISM_ADMIN_INITIAL_PASSWORD must be at least 10 characters.");
        }
        String normalized = PhoneNumbers.normalize(phone);
        if (users.findByPhoneNumber(normalized).isPresent()) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.fullName = properties.getAdmin().getInitialName();
        admin.phoneNumber = normalized;
        admin.passwordHash = passwordEncoder.encode(password);
        admin.status = "ACTIVE";
        users.save(admin);
        accounts.grant(admin.id, "ADMIN");
        log.info("Seeded the initial WebAux admin account");
    }
}
