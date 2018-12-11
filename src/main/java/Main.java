import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String [] args){

        Map<String, String> gKey = MetricExporter.export();
        List<Map<String, Object>> maps = new ArrayList<>();
        try {
            URL url = new URL("http://localhost:9091/metrics");
            MetricAnalyser analyser = new MetricAnalyser();
            analyser.setMetricProperties("TestJob", "", gKey);
            maps =  analyser.getMetricMaps(url, MetricType.assignMetricType("histogram"),
                    "test_histogram","Creating histogram");

        } catch (MalformedURLException e) {
            System.out.println(e.getMessage());
        }

        if(!maps.isEmpty()) {
            for (Map<String, Object> metricMap : maps) {
                System.out.println(metricMap + "\n");
            }
        }
    }
}
