from fastapi import FastAPI
from pydantic import BaseModel
import random
import time

app = FastAPI(
    title="SmartBancs AI Service Mock",
    description="Servicio asíncrono de evaluación de riesgo e IA para transferencias",
    version="1.0.0"
)

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

@app.get("/")
@app.get("/health")
def health_check():
    return {"status": "UP", "service": "ai-service", "version": "1.0.0"}

@app.post("/api/v1/risk-assessments", response_model=RiskAssessmentResponse)
def assess_risk(request: RiskAssessmentRequest):
    start_time = time.time()
    
    # Simula latencia variable entre 100 y 500 ms
    latency = random.uniform(0.1, 0.5)
    time.sleep(latency)
    
    # Regla simple de scoring
    risk_score = round(random.uniform(0.01, 0.99), 4)
    recommendation = "APPROVE" if risk_score < 0.75 else "FLAG_FOR_REVIEW"
    
    elapsed_ms = round((time.time() - start_time) * 1000, 2)
    
    return RiskAssessmentResponse(
        transaction_id=request.transaction_id,
        risk_score=risk_score,
        recommendation=recommendation,
        model_version="mock-risk-v1",
        processing_time_ms=elapsed_ms
    )
