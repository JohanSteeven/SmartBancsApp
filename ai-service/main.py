import os
import time
import random
import logging
import requests
from fastapi import FastAPI, Query, HTTPException
from pydantic import BaseModel
from typing import Optional

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai-service")

app = FastAPI(
    title="SmartBancs AI Service",
    description="Servicio asíncrono de evaluación de riesgo e IA utilizando Gemini API con motor de reglas sintético de respaldo",
    version="1.1.0"
)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", os.getenv("AI_SERVICE_API_KEY", ""))

class RiskAssessmentRequest(BaseModel):
    transaction_id: str
    amount: float
    currency: str
    source_account_id: str
    destination_account_id: str

class RiskAssessmentResponse(BaseModel):
    transaction_id: str
    risk_score: float
    recommendation: str
    model_version: str
    processing_time_ms: float
    notes: Optional[str] = None

@app.get("/")
@app.get("/health")
def health_check():
    gemini_status = "CONFIGURED" if GEMINI_API_KEY else "NOT_CONFIGURED"
    return {
        "status": "UP",
        "service": "ai-service",
        "gemini_integration": gemini_status,
        "version": "1.1.0"
    }

def call_gemini_api(amount: float, currency: str) -> Optional[dict]:
    if not GEMINI_API_KEY:
        return None
    try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={GEMINI_API_KEY}"
        prompt = (
            f"Evalúa la siguiente transacción bancaria para detección de fraude: "
            f"Monto: {amount} {currency}. Devuelve solo un JSON con las claves: "
            f"risk_score (numero float entre 0.0 y 1.0), recommendation ('APPROVE', 'FLAG_FOR_REVIEW' o 'REJECT'), "
            f"y reason (breve explicación de 10 palabras max)."
        )
        headers = {"Content-Type": "application/json"}
        payload = {
            "contents": [{"parts": [{"text": prompt}]}]
        }
        res = requests.post(url, json=payload, headers=headers, timeout=4.0)
        if res.status_code == 200:
            data = res.json()
            text_resp = data["candidates"][0]["content"]["parts"][0]["text"]
            # Limpieza básica de respuesta markdown json
            clean_json = text_resp.replace("```json", "").replace("```", "").strip()
            import json
            parsed = json.loads(clean_json)
            return {
                "risk_score": float(parsed.get("risk_score", 0.15)),
                "recommendation": str(parsed.get("recommendation", "APPROVE")),
                "reason": str(parsed.get("reason", "Análisis completado por Gemini AI")),
                "model": "gemini-1.5-flash"
            }
    except Exception as e:
        logger.warning(f"Fallback activado: Error invocando Gemini API ({str(e)})")
    return None

@app.post("/api/v1/risk-assessments", response_model=RiskAssessmentResponse)
def assess_risk(
    request: RiskAssessmentRequest,
    simulate_error: bool = Query(False, description="Simula un fallo 500 para pruebas de inyección de fallos")
):
    start_time = time.time()

    if simulate_error:
        raise HTTPException(status_code=500, detail="Inyección sintética de error 500 para pruebas de resiliencia")

    # Simulación de latencia variable entre 100 y 800 ms
    latency = random.uniform(0.1, 0.8)
    time.sleep(latency)

    # Intenta evaluar con Gemini API
    gemini_result = call_gemini_api(request.amount, request.currency)

    if gemini_result:
        risk_score = round(gemini_result["risk_score"], 4)
        recommendation = gemini_result["recommendation"]
        model_version = gemini_result["model"]
        notes = gemini_result["reason"]
    else:
        # Motor de reglas sintético (Fallback)
        if request.amount > 5000:
            risk_score = round(random.uniform(0.70, 0.95), 4)
            recommendation = "FLAG_FOR_REVIEW"
        else:
            risk_score = round(random.uniform(0.01, 0.35), 4)
            recommendation = "APPROVE"
        model_version = "mock-risk-v1"
        notes = "Evaluado por motor de reglas de riesgo (Fallback)"

    elapsed_ms = round((time.time() - start_time) * 1000, 2)

    return RiskAssessmentResponse(
        transaction_id=request.transaction_id,
        risk_score=risk_score,
        recommendation=recommendation,
        model_version=model_version,
        processing_time_ms=elapsed_ms,
        notes=notes
    )
