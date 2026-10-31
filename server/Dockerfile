FROM python:3.12-slim
WORKDIR /app
COPY sautique_server.py .
ENV SAUTIQUE_DB=/data/scores.db SAUTIQUE_PORT=8080 PYTHONUNBUFFERED=1
VOLUME /data
EXPOSE 8080
CMD ["python", "sautique_server.py"]
