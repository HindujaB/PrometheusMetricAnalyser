import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String [] args){

        Map<String, String> gKey = MetricExporter.export();

        try {
            URL url = new URL("http://localhost:9091/metrics");
            printMetricMap("counter", url, gKey);
            printMetricMap("gauge", url, gKey);
            printMetricMap("histogram", url, gKey);
            printMetricMap("summary", url, gKey);
        } catch (MalformedURLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void printMetricMap(String metricType,
                                       URL url, Map<String, String> gKey) {
        List<Map<String, Object>> mapList;
        System.out.println("----------------------------------------------------------------------------------");
        System.out.println("Maps created for " + metricType + " metric type");
        System.out.println("----------------------------------------------------------------------------------");
        MetricAnalyser analyser = new MetricAnalyser();
        analyser.setMetricProperties("TestJob", "", gKey);
        mapList =  analyser.getMetricMaps(url, MetricType.assignMetricType(metricType),
                "test_" + metricType,"Creating " + metricType);
        if(!mapList.isEmpty()) {
            for (Map<String, Object> metricMap : mapList) {
                System.out.println(metricMap + "\n");
            }
        }
    }
}
