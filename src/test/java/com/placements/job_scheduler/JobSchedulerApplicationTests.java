package com.placements.job_scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"worker.instance-id=test-instance", "worker.count=1"})
@ActiveProfiles("test")
class JobSchedulerApplicationTests {

	@Test
	void contextLoads() {
	}
}