from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    app_env: str = "development"
    verifier_version: str = "0.7.0"
    verification_contract_version: str = "1.2"
    knowledge_snapshot: str = "LIVE-API"
    database_path: str = "./data/medical_verifier.db"

    ncbi_tool: str = "medical-verifier"
    ncbi_email: str = "change-me@example.com"
    ncbi_api_key: str = ""

    openfda_api_key: str = ""

    max_evidence_items: int = 12
    evidence_timeout_seconds: float = 10.0
    high_risk_requires_review: bool = True

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()
