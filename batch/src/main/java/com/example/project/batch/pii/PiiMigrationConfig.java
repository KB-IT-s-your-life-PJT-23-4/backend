package com.example.project.batch.pii;

import com.example.project.batch.config.BatchDataSourceConfig;
import com.example.project.user.crypto.UserPiiCryptoService;
import com.example.project.user.crypto.UserPiiHmacService;
import com.example.project.user.crypto.UserPiiKeyProvider;
import com.example.project.user.crypto.UserPiiProtectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import(BatchDataSourceConfig.class)
@PropertySource({"classpath:database.properties", "classpath:application.properties"})
public class PiiMigrationConfig {

    @Bean
    public UserPiiKeyProvider userPiiKeyProvider(
            @Value("${ai.conversation.crypto.active-key-id}") String activeKeyId,
            @Value("${ai.conversation.crypto.key-v1}") String keyV1,
            @Value("${ai.conversation.crypto.key-v2:}") String keyV2,
            @Value("${pii.search.hmac-key-v1:}") String hmacKeyV1
    ) {
        return new UserPiiKeyProvider(activeKeyId, keyV1, keyV2, hmacKeyV1);
    }

    @Bean
    public UserPiiCryptoService userPiiCryptoService(UserPiiKeyProvider keyProvider) {
        return new UserPiiCryptoService(keyProvider);
    }

    @Bean
    public UserPiiHmacService userPiiHmacService(UserPiiKeyProvider keyProvider) {
        return new UserPiiHmacService(keyProvider);
    }

    @Bean
    public UserPiiProtectionService userPiiProtectionService(
            UserPiiCryptoService cryptoService,
            UserPiiHmacService hmacService
    ) {
        return new UserPiiProtectionService(cryptoService, hmacService);
    }

    @Bean
    public PiiMigrationService piiMigrationService(
            PiiMigrationMapper mapper,
            UserPiiProtectionService protectionService,
            PlatformTransactionManager transactionManager
    ) {
        return new PiiMigrationService(
                mapper,
                protectionService,
                new TransactionTemplate(transactionManager)
        );
    }
}
