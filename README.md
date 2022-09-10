# alertmanager-ruler-proxy

## Example Config

```json
{
  "prometheusUrl": "http://prometheus",
  "rulePath": "rules.yml"
}
```

## Notes

### Grafana Ruler API

https://grafana.com/docs/mimir/latest/operators-guide/reference-http-api/#ruler

### Running a Mimir Container

```sh
docker run -ti --rm -p 9009:9009 grafana/mimir -server.http-listen-address=0.0.0.0 -server.http-listen-port=9009 -auth.multitenancy-enabled=false
```
