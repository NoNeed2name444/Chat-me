from abc import ABC, abstractmethod

from app.models.evidence import EvidenceItem

class EvidenceProvider(ABC):
    name: str = "unknown"

    @abstractmethod
    def search(
        self,
        claim: str,
        concepts: list[str],
        limit: int,
    ) -> list[EvidenceItem]:
        raise NotImplementedError
