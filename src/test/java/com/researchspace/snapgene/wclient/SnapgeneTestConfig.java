package com.researchspace.snapgene.wclient;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration

public class SnapgeneTestConfig {
	private @Autowired Environment environment;

	public @Bean SnapgeneWSClient SnapgeneWSClient() {
    //ws.init();
		return new SnapgeneWSClientImpl(environment.getProperty("snapgene.web.url", URI.class),
				() -> "SnapgeneWSClient-ITTest");
	}

}
