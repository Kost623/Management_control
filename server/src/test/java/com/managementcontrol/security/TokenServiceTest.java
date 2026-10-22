package com.managementcontrol.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    @Test
    void генерує_унікальні_токени_з_префіксом() {
        String first = TokenService.generateDeviceToken();
        String second = TokenService.generateDeviceToken();

        assertThat(first).startsWith(TokenService.DEVICE_TOKEN_PREFIX);
        assertThat(second).startsWith(TokenService.DEVICE_TOKEN_PREFIX);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void хеш_детермінований_і_не_містить_сирий_токен() {
        String token = TokenService.generateDeviceToken();

        String hash = TokenService.hash(token);

        assertThat(hash).hasSize(64);
        assertThat(hash).doesNotContain(token);
        assertThat(TokenService.hash(token)).isEqualTo(hash);
        assertThat(TokenService.hash(token + "x")).isNotEqualTo(hash);
    }

    @Test
    void відрізняє_токен_пристрою_від_jwt() {
        assertThat(TokenService.isDeviceToken(TokenService.generateDeviceToken())).isTrue();
        assertThat(TokenService.isDeviceToken("eyJhbGciOiJIUzI1NiJ9.abc.def")).isFalse();
        assertThat(TokenService.isDeviceToken(null)).isFalse();
    }
}
