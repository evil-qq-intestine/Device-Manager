package com.example.tool;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef")
class ToolApplicationTests {

	@Test
	void contextLoads() {
	}

}
