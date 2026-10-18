package com.example.demo.config;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Creates the booking/billing/wallet tables, functions, triggers and views once Hibernate has made users and slots. */
@Component
@Order(1)
public class WalletSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    public WalletSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("wallet_schema.sql"));
        populator.setSeparator("//");
        populator.execute(dataSource);
    }
}
