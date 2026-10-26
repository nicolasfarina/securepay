FROM golang:1.27.3-alpine@sha256:b8c9b82affbbb82a020bbcf998dad2ba9516944ac7e883524642a481a9505e9c AS build
WORKDIR /src
# Upstream v0.34.1, pinned to its release commit and the archive checksum.
ADD --checksum=sha256:5c5c72ec6b4c4746d523f2ed1994dfec4b94f7457e530ee6fb16cfec158e545e https://codeload.github.com/prometheus/alertmanager/tar.gz/73c6bfe7393929211294c1954f30d8ed78e4d0ad /tmp/source.tar.gz
RUN tar -xzf /tmp/source.tar.gz --strip-components=1 -C /src
# This lock file updates x/net and grpc; the newer Go compiler fixes the stdlib findings.
COPY monitoring/alertmanager/go.mod monitoring/alertmanager/go.sum /src/
RUN mkdir -p ui/app/dist && echo 'Local lab: use Prometheus for the alert view.' > ui/app/dist/index.html \
    && CGO_ENABLED=0 go build -mod=readonly -trimpath -o /alertmanager ./cmd/alertmanager
FROM scratch
COPY --from=build /alertmanager /bin/alertmanager
USER 65534:65534
ENTRYPOINT ["/bin/alertmanager"]
