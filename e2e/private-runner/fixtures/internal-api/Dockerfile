FROM docker.io/library/python:3.13.7-alpine

WORKDIR /app
COPY server.py /app/server.py

USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["python3", "/app/server.py"]
