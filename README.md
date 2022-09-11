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
docker run -ti --rm -e MINIO_ROOT_USER=minio -e MINIO_ROOT_PASSWORD=12345678 quay.io/minio/minio server /data --address 0.0.0.0:9000
docker run -ti --rm -p 9009:9009 grafana/mimir -server.http-listen-address=0.0.0.0 -server.http-listen-port=9009 -auth.multitenancy-enabled=false -ruler-storage.backend s3 -ruler-storage.s3.access-key-id minio -ruler-storage.s3.secret-access-key 12345678 -ruler-storage.s3.bucket-name test -ruler-storage.s3.endpoint 10.1.2.21:9000
```
