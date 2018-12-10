import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.PushGateway;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class MetricExporter {

    static CollectorRegistry registry = new CollectorRegistry();

    private static void buildMetrics() {


        Counter counter = Counter.build()
                .name("test_counter")
                .help("Creating counter")
                .labelNames("name", "age")
                .register(registry);
        counter.labels("Jim", "21").inc(10);
        counter.labels("Hindu", "22").inc(12);

        Gauge gauge = Gauge.build()
                .name("test_gauge")
                .help("Creating gauge")
                .labelNames("name", "age")
                .register(registry);
        gauge.labels("Jim", "21").inc(10);
        gauge.labels("Jim", "21").dec(5);

        Histogram histogram = Histogram.build()
                .name("test_histogram")
                .help("Creating histogram")
                .labelNames("name", "age")
                .buckets(2, 4, 6, 8)
                .register(registry);
        histogram.labels("Jim", "21").observe(5);
        histogram.labels("Jack", "23").observe(4);
        histogram.labels("Hindu", "15").observe(3);

        Summary summary = Summary.build()
                .name("test_summary")
                .help("Creating summary")
                .labelNames("name", "age")
                .quantile(.65, 0.001)
                .quantile(0.5, 0.001)
                .quantile(0.32, 0.02)
                .quantile(0.98, 0.0001)
                .register(registry);
        summary.labels("Jim", "21").observe(5);
        summary.labels("Jack", "23").observe(4);
        summary.labels("Hindu", "15").observe(3);

    }


    public static Map<String, String> export() {

        Map<String, String> groupingKey = new HashMap<>();
        groupingKey.put("key1", "value1");
        groupingKey.put("key2", "value2");
        try {
            PushGateway PG = new PushGateway("localhost:9091");
            buildMetrics();
            PG.push(registry, "TestJob", groupingKey);
            System.out.println("metric created successfully");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return groupingKey;

    }
}
