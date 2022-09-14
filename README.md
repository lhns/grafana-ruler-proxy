# grafana-ruler-proxy

## Example Config

```json
{
  "prometheus": {
    "url": "http://prometheus:9090",
    "rulePath": "/prometheus/rules.yml",
    "internalRulePath": "/config/rules.yml"
  },
  "alertmanager": {
    "url": "http://alertmanager:9093",
    "configPath": "/alertmanager/alertmanager.yml"
  }
}
```

### Grafana Ruler API

https://grafana.com/docs/mimir/latest/operators-guide/reference-http-api/#ruler
