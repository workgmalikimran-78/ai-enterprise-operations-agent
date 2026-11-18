package com.ai.aiops.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class IncidentAnomalyId implements Serializable {

    private UUID incidentId;
    private UUID anomalyId;

    public IncidentAnomalyId(UUID incidentId, UUID anomalyId) {
        this.incidentId = incidentId;
        this.anomalyId = anomalyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncidentAnomalyId that)) return false;
        return Objects.equals(incidentId, that.incidentId) && Objects.equals(anomalyId, that.anomalyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(incidentId, anomalyId);
    }
}
