# grafana-ruler-proxy

Use the Grafana Alert Rule Editor with Prometheus or VictoriaMetrics.
Mimir natively supports the Grafana Ruler API to edit rules and alerts and this proxy brings that functionality to other TSDBs like Prometheus or VictoriaMetrics.
The Proxy will act as the Grafana data source and forward all metrics queries to the TSDB but it will intercept the Grafana UI Ruler calls and update the rule and alert configuration files.

## Prometheus Example Config

```json
{
  "prometheus": {
    "url": "http://prometheus:9090",
    "rulePath": "/grafana-ruler-proxy-config/rules.yml",
    "internalRulePath": "/config/rules.yml",
    "namespace": "prometheus"
  },
  "alertmanager": {
    "url": "http://alertmanager:9093",
    "configPath": "/alertmanager/alertmanager.yml"
  }
}
```

## VictoriaMetrics Example Config

```json
{
  "prometheus": {
    "url": "http://victoriametrics:8428",
    "rulesUrl": "http://vmalert:8880",
    "rulePath": "/grafana-ruler-proxy-config/rules.yml",
    "internalRulePath": "/vmalert-config/rules.yml",
    "namespace": "vmalert"
  },
  "alertmanager": {
    "url": "http://alertmanager:9093",
    "configPath": "/alertmanager/alertmanager.yml"
  }
}
```

## Grafana Ruler API

https://grafana.com/docs/mimir/latest/operators-guide/reference-http-api/#ruler

## License

This project uses the Apache 2.0 License. See the file called LICENSE.
