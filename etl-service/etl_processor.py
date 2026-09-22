import os
import sys
import uuid
import json
import argparse
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import pandas as pd
from sqlalchemy import create_engine, text

def get_db_engine():
    db_url = os.getenv(
        "DATABASE_URL",
        "postgresql://postgres:postgrespassword@localhost:5432/smartbancs"
    )
    # Reemplaza postgres:5432 por localhost si se ejecuta localmente fuera de docker
    if "postgres:5432" in db_url and not os.path.exists("/.dockerenv"):
        db_url = db_url.replace("postgres:5432", "localhost:5432")
    return create_engine(db_url)

def validate_uuid(val):
    try:
        uuid.UUID(str(val))
        return True
    except (ValueError, AttributeError):
        return False

def validate_currency(val):
    return isinstance(val, str) and len(val.strip()) == 3 and val.strip().isupper()

def process_batch(file_path, batch_id=None):
    if not batch_id:
        batch_id = f"BATCH-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}"

    print(f"=== Iniciando Pipeline ETL Bancs ===")
    print(f"Archivo: {file_path}")
    print(f"ID de Lote: {batch_id}")

    if not os.path.exists(file_path):
        print(f"Error: No se encontró el archivo {file_path}")
        sys.exit(1)

    # Ingestión con Pandas
    if file_path.endswith('.csv'):
        df = pd.read_csv(file_path, dtype=str)
    elif file_path.endswith('.json'):
        df = pd.read_json(file_path, dtype=str)
    else:
        print("Error: Formato de archivo no soportado. Use CSV o JSON.")
        sys.exit(1)

    print(f"Registros leídos: {len(df)}")

    valid_records = []
    rejected_records = []
    seen_transactions = set()

    for idx, row in df.iterrows():
        raw_dict = row.to_dict()
        errors = []

        # 1. Validación de nulos
        required_fields = ['transaction_id', 'source_account_id', 'destination_account_id', 'amount', 'currency']
        for field in required_fields:
            if pd.isna(row.get(field)) or str(row.get(field)).strip() == "":
                errors.append(f"Campo obligatorio nulo o vacío: {field}")

        tx_id_str = str(row.get('transaction_id', '')).strip()
        source_id_str = str(row.get('source_account_id', '')).strip()
        dest_id_str = str(row.get('destination_account_id', '')).strip()
        amount_str = str(row.get('amount', '')).strip()
        currency_str = str(row.get('currency', '')).strip()

        # 2. Validación de UUIDs
        if tx_id_str and not validate_uuid(tx_id_str):
            errors.append(f"Formato UUID de transaction_id inválido: {tx_id_str}")
        if source_id_str and not validate_uuid(source_id_str):
            errors.append(f"Formato UUID de source_account_id inválido: {source_id_str}")
        if dest_id_str and not validate_uuid(dest_id_str):
            errors.append(f"Formato UUID de destination_account_id inválido: {dest_id_str}")

        # 3. Validación de duplicados en el lote
        if tx_id_str in seen_transactions:
            errors.append(f"Transacción duplicada en el lote: {tx_id_str}")
        else:
            if tx_id_str:
                seen_transactions.add(tx_id_str)

        # 4. Validación de Monto a Decimal positivo
        try:
            amount_dec = Decimal(amount_str)
            if amount_dec <= 0:
                errors.append(f"Monto debe ser positivo: {amount_str}")
        except (InvalidOperation, TypeError):
            errors.append(f"Monto con formato numérico inválido: {amount_str}")
            amount_dec = None

        # 5. Validación de Moneda ISO-4217
        if not validate_currency(currency_str):
            errors.append(f"Moneda debe ser código ISO-4217 de 3 letras mayúsculas: {currency_str}")

        if errors:
            rejected_records.append({
                "id": str(uuid.uuid4()),
                "batch_id": batch_id,
                "raw_record": json.dumps(raw_dict),
                "error_code": "VALIDATION_FAILED",
                "error_message": " | ".join(errors),
                "created_at": datetime.now(timezone.utc)
            })
        else:
            valid_records.append({
                "id": str(uuid.uuid4()),
                "transaction_id": tx_id_str,
                "source_account_id": source_id_str,
                "destination_account_id": dest_id_str,
                "amount": float(amount_dec),
                "currency": currency_str,
                "status": "ETL_PROCESSED",
                "processed_at": datetime.now(timezone.utc)
            })

    # Persistencia en PostgreSQL mediante SQLAlchemy
    engine = get_db_engine()

    with engine.begin() as conn:
        if valid_records:
            valid_df = pd.DataFrame(valid_records)
            valid_df.to_sql("bancs_sync_staging", conn, if_exists="append", index=False)
            print(f"[OK] {len(valid_records)} registros válidos insertados en bancs_sync_staging.")

        if rejected_records:
            rej_df = pd.DataFrame(rejected_records)
            rej_df.to_sql("bancs_etl_errors", conn, if_exists="append", index=False)
            print(f"[REJECTED] {len(rejected_records)} registros rechazados insertados en bancs_etl_errors.")

    print("=== Resumen de Ejecución ETL ===")
    print(f"Total procesados : {len(df)}")
    print(f"Registros válidos: {len(valid_records)}")
    print(f"Rechazados       : {len(rejected_records)}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pipeline ETL de Procesamiento Financiero Bancs")
    parser.add_argument("--file", type=str, default="data/sample_batch.csv", help="Ruta al archivo CSV o JSON de entrada")
    parser.add_argument("--batch-id", type=str, default=None, help="Identificador único del lote")

    args = parser.parse_args()
    process_batch(args.file, args.batch_id)
