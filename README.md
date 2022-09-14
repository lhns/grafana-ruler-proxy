# grafana-ruler-proxy

## Example Config

```json
{
  "prometheus": {
    "url": "http://prometheus",
    "rulePath": "rules.yml",
    "internalRulePath": "/config/rules.yml"
  },
  "alertmanager": {
    "url": "http://alertmanager",
    "configPath": "alerts.yml"
  }
}
```

### Grafana Ruler API

https://grafana.com/docs/mimir/latest/operators-guide/reference-http-api/#ruler
