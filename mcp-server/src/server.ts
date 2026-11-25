import { McpServer } from '@modelcontextprotocol/server';
import { registerLogTools } from './tools/logs.js';
import { registerAnomalyTools } from './tools/anomalies.js';
import { registerIncidentTools } from './tools/incidents.js';
import { registerApprovalTools } from './tools/approvals.js';
import { registerRemediationTools } from './tools/remediations.js';
import { registerAdminTools } from './tools/admin.js';
import { registerDashboardTools } from './tools/dashboard.js';
import { registerAuditTools } from './tools/audit.js';
import { registerWarehouseTools } from './tools/warehouse.js';

/** Builds a fresh McpServer with every tool registered - called once per request (stateless). */
export function buildServer(): McpServer {
    const server = new McpServer({ name: 'aiops-mcp-server', version: '0.1.0' });
    registerLogTools(server);
    registerAnomalyTools(server);
    registerIncidentTools(server);
    registerApprovalTools(server);
    registerRemediationTools(server);
    registerAdminTools(server);
    registerDashboardTools(server);
    registerAuditTools(server);
    registerWarehouseTools(server);
    return server;
}
