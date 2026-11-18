package com.ai.aiops.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "incident_anomalies")
@Getter
@Setter
@NoArgsConstructor
public class IncidentAnomaly {

    @EmbeddedId
    private IncidentAnomalyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("incidentId")
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("anomalyId")
    @JoinColumn(name = "anomaly_id")
    private Anomaly anomaly;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IncidentAnomaly(Incident incident, Anomaly anomaly) {
        this.incident = incident;
        this.anomaly = anomaly;
        this.id = new IncidentAnomalyId(incident.getId(), anomaly.getId());
    }
}
