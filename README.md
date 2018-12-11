# PrometheusMetricAnalyser
This app analyses the metrics exported from a URL and generates java Maps according to the filtered metrics

## Description
* The application exports some Prometheus metrics using Prometheus Pushgateway and receives the exported metrics using an http request.
* Then it analyses the received response and filter the metrics according to the user defined metric name, metric type, job, instance and grouping keys.
* Finally, it generates java maps according to different label values of the filtered metrics and print them.

## Prerequisites
* A Prometheus pushgateway instance must be started and running at 'http://localhost:9091/'
