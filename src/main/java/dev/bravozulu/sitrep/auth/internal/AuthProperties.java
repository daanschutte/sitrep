package dev.bravozulu.sitrep.auth.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("sitrep.auth")
public record AuthProperties(@DefaultValue("48h") Duration activationTokenTtl) {}
