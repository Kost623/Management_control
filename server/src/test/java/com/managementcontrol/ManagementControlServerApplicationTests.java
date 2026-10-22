package com.managementcontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ManagementControlServerApplicationTests {

    @Test
    void контекст_піднімається() {
        // Smoke-тест: Spring-контекст з усіма компонентами збирається без помилок
    }
}
