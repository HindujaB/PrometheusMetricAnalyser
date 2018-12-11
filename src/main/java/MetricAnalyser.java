

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class analyses the response from http server, filter the metrics and generate maps according to
 * metric label-values.
 */
class MetricAnalyser {

    private List<String> metricResponse = new ArrayList<>();
    private String metricName;
    private String metricJob;
    private String metricInstance;
    private MetricType metricType;
    private Map<String, String> metricGroupingKey;
    private List<Map<String, Object>> metricMaps = new ArrayList<>();


    private Map<String, Object> generateMap(Map<String, String> metricLabels, Double metricValue, Double count,
                                            Double sum, Map<String, Double> quantiles, Map<String, Double> buckets) {
        Map<String, Object> metricMap = new LinkedHashMap<>();
        metricMap.put("MetricName", this.metricName );
        metricMap.put("Job", this.metricJob);
        metricMap.put("Instance", this.metricInstance);
        metricMap.putAll(metricLabels);
        switch (metricType) {
            case COUNTER:
            case GAUGE: {
                metricMap.put("Value", metricValue);
                break;
            }
            case HISTOGRAM: {
                metricMap.put("Count", count);
                metricMap.put("Sum", sum);
                metricMap.putAll(buckets);
                break;
            }
            case SUMMARY: {
                metricMap.put("Count", count);
                metricMap.put("Sum", sum);
                metricMap.putAll(quantiles);
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

    /**
     * filter metrics into maps using label values
     */
    private void analyseMetrics(List<String> metricSamples) {

        Map<String, String> labelValues;
        labelValues = setIdealSample(metricSamples.get(0));
        Map<String, String> metricLabels = new LinkedHashMap<>();
        Double metricValue = 0.0;
        Double count = 0.0;
        Double sum = 0.0;
        Map<String, Double> quantiles = new LinkedHashMap<>();
        Map<String, Double> buckets = new LinkedHashMap<>();
        for (String sample : metricSamples) {
            String sampleName = sample.substring(0, sample.indexOf("{"));
            Double value = Double.parseDouble(sample.substring(sample.indexOf("}") + 1));
            Map<String, String> labelPairMap = filterMetric(sample);
            labelPairMap.remove("job");
            labelPairMap.remove("instance");
            if (metricGroupingKey != null) {
                for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                    labelPairMap.remove(entry.getKey());
                }
            }
            MapDifference<String, String> mapDifference = Maps.difference(labelValues, labelPairMap);
            Map<String, MapDifference.ValueDifference<String>> valueDifferenceMap = mapDifference
                    .entriesDiffering();
            if (valueDifferenceMap.size() != 0) {
                if(valueDifferenceMap.containsKey("quantile") || valueDifferenceMap.containsKey("le")){
                    valueDifferenceMap.remove("quantile");
                    valueDifferenceMap.remove("le");
                }
            }
            if (!valueDifferenceMap.isEmpty()) {
                metricMaps.add(generateMap(metricLabels, metricValue, count, sum, quantiles, buckets));
                labelValues = setIdealSample(sample);
            }

            if (labelPairMap.containsKey("quantile")) {
                quantiles.put("quantile_" + labelPairMap.get("quantile"), value);
                labelPairMap.remove("quantile");
            } else if (labelPairMap.containsKey("le")) {
                buckets.put("bucket_" + labelPairMap.get("le"), value);
                labelPairMap.remove("le");
            } else {
                metricValue = value;
            }
            if (sampleName.equals(metricName + "_sum")) {
                    sum = value;
            } else if (sampleName.equals(metricName + "_count")) {
                    count = value;
            }
            metricLabels.putAll(labelPairMap);
            if (metricSamples.indexOf(sample) == metricSamples.size() -1) {
                metricMaps.add(generateMap(metricLabels, metricValue, count, sum, quantiles, buckets));
            }
        }
    }

    /**
     * Set sample label values to identify metric value changes.
     * @param sample A single sample form the Prometheus response
     * @return return a label -> value map of the sample
     */
    private Map<String, String> setIdealSample(String sample) {
        Map<String, String> idealSample = filterMetric(sample);
        idealSample.remove("job");
        idealSample.remove("instance");
        if (metricGroupingKey != null) {
            for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                idealSample.remove(entry.getKey());
            }
        }
        idealSample.remove("quantile");
        idealSample.remove("le");
        return idealSample;
    }

    List<Map<String, Object>> getMetricMaps(URL url, MetricType metricType, String metricName, String help) {
        requestMetric(url);
        retrieveMetric(metricType, metricName, help);
        return metricMaps;
    }

    /**
     *  filter metrics using metric name and help strings
     */
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
        List<String> metrics = validateMetric(index);
        analyseMetrics(metrics);
    }

    /**
     * filter metrics using metric type, job, instance and grouping key values.
     * @param index index of the required metric from response
     * @return return filtered metrics in a String list
     */
    private List<String> validateMetric(Integer index) {
        List<String> requiredMetrics = new ArrayList<>();
        boolean metricIdentifier = false;
        List<String> retrievedMetrics = metricResponse.stream().filter(response -> response.startsWith(metricName))
                .collect(Collectors.toList());
        if (metricResponse.get(index + 1).contains("# TYPE " + metricName + " " +
                    MetricType.getMetricTypeString(metricType))) {
            if(!(metricJob.equals("") && metricInstance.equals("") && metricGroupingKey == null) ) {
                for (String singleSample : retrievedMetrics) {
                    Map<String, String> labelPairMap = filterMetric(singleSample);
                    if (!(metricJob.equals(""))) {
                        metricIdentifier = labelPairMap.get("job").equalsIgnoreCase(metricJob);
                    }
                    if (!(metricInstance.equals(""))) {
                        metricIdentifier = labelPairMap.get("instance").equalsIgnoreCase(metricInstance);
                    }
                    if (metricGroupingKey != null) {
                        for (Map.Entry<String, String> entry : metricGroupingKey.entrySet()) {
                            String value = labelPairMap.get(entry.getKey());
                            if (value != null) {
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
                        requiredMetrics.add(singleSample);
                    }
                }
            } else {
                requiredMetrics = retrievedMetrics;
            }
            }
        return requiredMetrics;
    }

    private Map<String, String> filterMetric(String metricSample) {
        String[] labelList = metricSample.substring(metricSample.indexOf("{") +1, metricSample.indexOf("}"))
                .split(",");
        Map<String, String> labelMap = new LinkedHashMap<>();
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

    /**
     * request metrics from the http server at the URL
     */
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
