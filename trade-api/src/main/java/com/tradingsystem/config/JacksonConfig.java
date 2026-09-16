//package com.tradingsystem.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class JacksonConfig {
//
//    @Bean
//    public ObjectMapper objectMapper() {
//        // Create default ObjectMapper and let it discover/register available modules
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.findAndRegisterModules(); // picks up JavaTimeModule etc. if present
//        return mapper;
//    }
//}