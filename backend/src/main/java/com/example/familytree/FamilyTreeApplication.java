package com.example.familytree;

import com.example.familytree.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.example.familytree.repo.Repositories;

@SpringBootApplication
@EnableJpaRepositories(basePackageClasses=Repositories.class, considerNestedRepositories=true)
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class FamilyTreeApplication {
    public static void main(String[] args) { SpringApplication.run(FamilyTreeApplication.class, args); }
}
