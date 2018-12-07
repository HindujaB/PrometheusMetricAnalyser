import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MetricAnalyser {

    private List<String> metricResponse = new ArrayList<>();
    private String metricName;
    private String metricJob;
    private String metricInstance;
    private MetricType metricType;
    private Map<String, String> metricGroupingKey;
    private Map<String, String> metricLabels = new HashMap<>();
    private Double metricValue;
    private Double count;
    private Double sum;
    private Map<String, Double> quantiles = new HashMap<>();
    private Map<String, Double> buckets = new HashMap<>();

    private Map<String, Object> generateMap() {
        Map<String, Object> metricMap = new HashMap<>();
        metricMap.put("Name", this.metricName);
        metricMap.put("Job", this.metricJob);
        metricMap.put("Instance", this.metricInstance);
        metricMap.putAll(this.metricLabels);
        switch (metricType) {
            case COUNTER:
            case GAUGE: {
                metricMap.put("Value", this.metricValue);
                break;
            }
            case HISTOGRAM: {
                metricMap.put("Count", this.count);
                metricMap.put("Sum", this.sum);
                metricMap.putAll(this.buckets);
                break;
            }
            case SUMMARY: {
                metricMap.put("Count", this.count);
                metricMap.put("Sum", this.sum);
                metricMap.putAll(this.quantiles);
                break;
            }
            default: //default will never be executed
        }
        return metricMap;
    }

    void setMetricProperties(String job, String instance, Map<String, String> groupingKey) {
        this.metricJob = job;
        this.metricInstance = instance;
        this.metricGroupingKey = groupingKey;
    }

    private void analyseMetrics(String metricSample) {

        String sampleName = metricSample.substring(0,metricSample.indexOf("{"));
        Double value = Double.parseDouble(metricSample.substring(metricSample.indexOf("}") + 1));
        Map<String, String> labelPairMap = filterMetric(metricSample);
        labelPairMap.remove("job");
        labelPairMap.remove("instance");
        for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
           labelPairMap.remove(entry.getKey());
        }
        if(sampleName.equals(metricName + "_sum")){
            this.sum = value;
        } else if(sampleName.equals(metricName + "_count")) {
            this.count = value;
        } else if(metricType.equals(MetricType.HISTOGRAM)){
            if(labelPairMap.containsKey("le")) {
                this.buckets.put("bucket_" + labelPairMap.get("le"),value);
                labelPairMap.remove("le");
            }
        } else if(metricType.equals(MetricType.SUMMARY)){
            if(labelPairMap.containsKey("quantile")) {
                this.quantiles.put("quantile_" + labelPairMap.get("quantile"),value);
                labelPairMap.remove("quantile");
            }
        } else {
            this.metricValue = value;
        }
        if (!this.metricLabels.equals(labelPairMap)) {
            this.metricLabels.putAll(labelPairMap);
        }
    }

    Map<String, Object> getMetricMap(URL url, MetricType metricType, String metricName, String help) {
        requestMetric(url);
        retrieveMetric(metricType, metricName, help);
        return generateMap();
    }

    private void retrieveMetric(MetricType metricType, String metricName, String help) {
        this.metricName = metricName;
        this.metricType = metricType;
        int index = -1;
        for (int i = 0; i < metricResponse.size(); i++) {
            if (("# HELP " + metricName + " " + help).equals(metricResponse.get(i))) {
                index = i;
            }
        }
        if (index == -1){
                throw new RuntimeException("Metric cannot be found");
        }
        Stream<String> metrics = validateMetric(index);
        metrics.forEach(this :: analyseMetrics);
    }

    private Stream<String> validateMetric(Integer index) {
        List<String> requiredMetrics = new ArrayList<>();
        boolean metricIdentifier = false;
        List<String> retrievedMetrics = metricResponse.stream().filter(response -> response.startsWith(metricName))
                .collect(Collectors.toList());
        if (metricResponse.get(index + 1).contains("# TYPE " + metricName + " " +
                    MetricType.getMetricTypeString(metricType))) {
            for (String singleMetric : retrievedMetrics) {
                Map<String, String> labelPairMap = filterMetric(singleMetric);
                if (!(metricJob.equals(""))) {
                    metricIdentifier = labelPairMap.get("job").equalsIgnoreCase(metricJob);
                }
                if (!(metricInstance.equals(""))) {
                    metricIdentifier = labelPairMap.get("instance").equalsIgnoreCase(metricInstance);
                }
                if (metricGroupingKey != null) {
                    for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                        String value = labelPairMap.get(entry.getKey());
                        if(value != null){
                            metricIdentifier = value.equalsIgnoreCase(entry.getValue());
                        } else {
                            //if the grouping key not found in the metric,
                            metricIdentifier = false;
                            break;
                        }
                        if (!metricIdentifier) {
                            //if the grouping key value is not matching,
                            break;
                        }
                    }
                }
                if (metricIdentifier) {
                    requiredMetrics.add(singleMetric);
                }
            }
        }
        return requiredMetrics.stream();
    }

    private Map<String, String> filterMetric(String metricSample) {
        String[] labelList = metricSample.substring(metricSample.indexOf("{") +1, metricSample.indexOf("}"))
                .split(",");
        Map<String, String> labelMap = new HashMap<>();
        Arrays.stream(labelList).forEach(labelEntry -> {
            String[] entry = labelEntry.split("=");
            if (entry.length == 2) {
                String label = entry[0];
                String value = entry[1].substring(1, entry[1].length() - 1);
                labelMap.put(label, value);
            } else {
                throw new RuntimeException("invalid format");
            }
        });
        return labelMap;
    }

    private void requestMetric(URL url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            if (conn.getResponseCode() != 200) {
                System.out.println("Http error: " + conn.getResponseCode() + "\n" + conn.getResponseMessage());
            } else {
                String inputLine;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                while ((inputLine = reader.readLine()) != null) {
                    metricResponse.add(inputLine);
                }
                reader.close();
            }
            conn.disconnect();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

}
