import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class Main {
    public static void main(String [] args){

        Map<String, String> gKey = MetricExporter.export();
        try {
            URL url = new URL("http://localhost:9091/metrics");
            MetricAnalyser analyser = new MetricAnalyser();
            analyser.setMetricProperties("TestJob", "", gKey);
            analyser.getMetricMap(url, MetricType.assignMetricType("histogram"),
                    "test_histogram","Creating histogram");

        } catch (MalformedURLException e) {
            System.out.println(e.getMessage());
        }
    }
}
