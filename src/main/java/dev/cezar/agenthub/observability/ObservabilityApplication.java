package dev.cezar.agenthub.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AgentHub Observability Service.
 * <p>
 * Serviço dedicado para coleta, armazenamento e consulta de traces e métricas
 * de execução de agentes, skills e tools.
 * </p>
 * <p>
 * Features:
 * - Execution, node, and tool traces
 * - Metric events (counter, gauge, timer, histogram)
 * - Aggregated metrics (SUM, AVG, MIN, MAX, COUNT, P50, P95, P99)
 * - Scheduled aggregation jobs
 * - Retention policies
 * </p>
 *
 * @since 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }
}
