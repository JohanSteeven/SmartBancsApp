FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY etl_processor.py .
COPY data/ data/

CMD ["python", "etl_processor.py"]
