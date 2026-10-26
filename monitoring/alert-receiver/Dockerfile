FROM golang:1.27.3-alpine@sha256:b8c9b82affbbb82a020bbcf998dad2ba9516944ac7e883524642a481a9505e9c AS build
WORKDIR /src
COPY monitoring/alert-receiver/receiver.go .
RUN CGO_ENABLED=0 go build -trimpath -o /receiver receiver.go
FROM scratch
COPY --from=build /receiver /receiver
USER 65534:65534
ENTRYPOINT ["/receiver"]
