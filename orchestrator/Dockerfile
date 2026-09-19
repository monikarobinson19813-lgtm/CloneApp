FROM python:3.12-slim

WORKDIR /opt/cloneapp
COPY orchestrator /opt/cloneapp/orchestrator
RUN pip install --no-cache-dir /opt/cloneapp/orchestrator

ENV CA_POLL_SECONDS=60
CMD ["ca-orchestrator", "--plan", "/opt/cloneapp/orchestrator/plan.example.json"]
